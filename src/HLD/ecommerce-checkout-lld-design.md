# E-commerce Checkout LLD

## Scope

This solution uses a **modular monolith with a relational database** as the
transaction boundary. The code is an in-memory implementation of the same
responsibilities:

`src\LLD\EcommerceCheckout`

The demo can be run with:

```text
LLD.EcommerceCheckout.demo.CheckoutDemo
```

## Architecture

```text
Checkout API
     |
CheckoutService
  |       |        |
Cart   Inventory  PaymentProcessor
                 |
          PaymentStrategy
          |       |       |
        Card     UPI    Wallet
                 |
          PaymentGateway adapter
```

`CheckoutService` is the application orchestration boundary. Domain objects
contain state and invariants; inventory owns stock transitions; payment
strategies hide method-specific gateway behavior.

## Implemented classes

### Models

- `CartItem`: immutable product, quantity, and price snapshot.
- `CheckoutRequest`: immutable checkout command and idempotency key.
- `Order`: aggregate root with `PENDING_PAYMENT`, `CONFIRMED`, and `CANCELLED`
  states.
- `CardDetails`, `UpiDetails`, `WalletDetails`: payment method-specific data.
- `PaymentDetails`: extension contract for new payment methods.

### Inventory

`InventoryService` maintains available quantities and reservations. Reservation
and release operations are synchronized so two concurrent checkout requests
cannot consume the same in-memory stock.

Production storage should use a conditional update:

```sql
UPDATE inventory
SET available_quantity = available_quantity - :quantity
WHERE product_id = :product_id
  AND available_quantity >= :quantity;
```

The caller must verify that the affected row count is correct.

### Payment

- `PaymentGateway`: provider adapter contract.
- `PaymentStrategy`: method-specific strategy contract.
- `GatewayPaymentStrategy`: binds one method to one gateway.
- `PaymentProcessor`: selects the matching strategy.

To add a new method:

1. Add a `PaymentDetails` implementation.
2. Add a gateway adapter.
3. Register a `GatewayPaymentStrategy`.

`CheckoutService` does not change.

### Checkout service

The flow is:

1. Check the idempotency key.
2. Create a pending order.
3. Reserve inventory.
4. Authorize payment.
5. Capture payment with bounded retries.
6. Commit the inventory reservation.
7. Confirm the order.
8. Save the idempotency result.

If a step fails, the service releases inventory and refunds an existing payment
authorization. In a real database implementation, the local state changes are
performed in a transaction and external payment outcomes are reconciled using
idempotency keys and callbacks.

## Transactional integrity

Within the modular monolith, the following local changes belong in one database
transaction:

```text
BEGIN
  Lock inventory rows
  Decrement/reserve stock
  Insert order
  Insert order items
  Insert payment authorization record
COMMIT
```

Payment provider calls should **not** hold database locks. The production flow is:

1. Create a short-lived inventory reservation and pending order.
2. Commit the local transaction.
3. Call the external provider with an idempotency key.
4. Process authorization/capture callbacks in idempotent transactions.
5. Confirm the order only after successful capture.
6. Release stock and cancel the order on permanent failure.
7. Refund and reconcile late provider success after expiry.

Strict ACID applies to local database state. An external gateway cannot
participate in the same database transaction, so payment integration requires
idempotency, compensation, and reconciliation.

## Concurrency

### Inventory

Use row-level locks or atomic conditional updates. Never trust a previously read
stock value.

### Order idempotency

Store a unique key:

```sql
CREATE UNIQUE INDEX ux_checkout_idempotency
ON checkout_request(idempotency_key);
```

If the same request is retried, return the original order rather than creating
a second order or charging the customer twice.

### Payment

Use provider idempotency keys for authorization and capture. Callback handling
must lock the payment/order row, check the current state, and safely ignore a
duplicate callback.

## Suggested relational schema

```sql
CREATE TABLE product (
    product_id       BIGSERIAL PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE inventory (
    product_id       BIGINT PRIMARY KEY REFERENCES product(product_id),
    available_qty    INTEGER NOT NULL CHECK (available_qty >= 0),
    version          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE orders (
    order_id         UUID PRIMARY KEY,
    user_id          VARCHAR(100) NOT NULL,
    status           VARCHAR(30) NOT NULL,
    total_amount     NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    currency         CHAR(3) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    order_id         UUID NOT NULL REFERENCES orders(order_id),
    product_id       BIGINT NOT NULL REFERENCES product(product_id),
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    unit_price       NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (order_id, product_id)
);

CREATE TABLE checkout_request (
    idempotency_key  VARCHAR(200) PRIMARY KEY,
    order_id         UUID NOT NULL UNIQUE REFERENCES orders(order_id),
    request_hash     VARCHAR(128) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    payment_id       UUID PRIMARY KEY,
    order_id         UUID NOT NULL REFERENCES orders(order_id),
    method           VARCHAR(30) NOT NULL,
    provider         VARCHAR(80) NOT NULL,
    provider_ref     VARCHAR(200),
    idempotency_key  VARCHAR(200) NOT NULL UNIQUE,
    status           VARCHAR(30) NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## State machines

### Order

```text
PENDING_PAYMENT -> CONFIRMED
PENDING_PAYMENT -> CANCELLED
```

### Payment

```text
INITIATED -> AUTHORIZED -> CAPTURED
INITIATED -> FAILED
AUTHORIZED -> REFUNDED
CAPTURED -> REFUNDED
```

### Inventory reservation

```text
AVAILABLE -> RESERVED -> COMMITTED
RESERVED  -> RELEASED
```

## REST API

### Create checkout

```http
POST /v1/checkouts
Idempotency-Key: checkout-abc-123
Content-Type: application/json
```

```json
{
  "userId": "user-1",
  "items": [
    { "productId": "sku-1", "quantity": 2 }
  ],
  "payment": {
    "method": "UPI",
    "vpa": "customer@upi"
  },
  "currency": "INR"
}
```

Successful response:

```http
201 Created
```

```json
{
  "orderId": "order-123",
  "status": "CONFIRMED",
  "paymentStatus": "CAPTURED",
  "total": "199.98",
  "currency": "INR"
}
```

### Get checkout status

```http
GET /v1/checkouts/{orderId}
```

Returns the current order and payment state. Clients should poll or consume an
event when payment is asynchronous.

### Cancel pending checkout

```http
POST /v1/orders/{orderId}/cancel
```

Cancellation is allowed only while the order is not confirmed. The service
releases its inventory reservation and initiates a refund when necessary.

### Payment callback

```http
POST /v1/payments/webhooks/{provider}
```

The callback is authenticated, deduplicated by provider event ID, and processed
idempotently.

## Error contract

```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "Product sku-1 is not available",
  "requestId": "req-123",
  "retryable": false
}
```

Typical errors:

| Code | Retryable | Meaning |
|---|---:|---|
| `INVALID_REQUEST` | No | Invalid item or payment data |
| `INSUFFICIENT_STOCK` | No | Inventory cannot satisfy the request |
| `PAYMENT_DECLINED` | No | Provider declined payment |
| `PAYMENT_TIMEOUT` | Yes | Provider response timed out |
| `DUPLICATE_REQUEST` | No | Same idempotency key with different payload |
| `CHECKOUT_IN_PROGRESS` | Yes | Existing request is still processing |

## Retry and recovery

- Retry only timeouts, connection failures, and provider 5xx responses.
- Use bounded exponential backoff with jitter.
- Never blindly retry a non-idempotent payment call.
- Pass the same provider idempotency key on every retry.
- Use a circuit breaker when a provider is unhealthy.
- Refund authorization if local finalization fails after authorization.
- Use an outbox table to publish `OrderConfirmed` and `PaymentFailed` events
  after the database transaction commits.

## SOLID and patterns

### Strategy

`PaymentStrategy` isolates payment-method behavior and allows new payment modes
without modifying checkout orchestration.

### Adapter

`PaymentGateway` adapters translate provider-specific APIs into a stable internal
contract.

### State

`Order.Status` represents the order lifecycle and guards legal transitions.
Production code can extract an explicit state object if transitions become more
complex.

### Facade/application service

`CheckoutService` is a use-case facade. It coordinates domain collaborators
without placing inventory or payment logic inside the order entity.

### Single Responsibility

Order state, inventory reservation, payment processing, and orchestration have
separate owners.

### Open/Closed and Dependency Inversion

Payment providers and methods are injected through interfaces. New integrations
are added by registering implementations rather than editing stable checkout
logic.

### Interface Segregation

`PaymentGateway` and `PaymentStrategy` expose only operations required by their
consumers.

## Scaling

- Keep catalogue reads on read replicas or a cache.
- Use the primary database for inventory authorization.
- Partition orders and payments by time or tenant at very high volume.
- Keep transactions short and never call external providers while holding locks.
- Use connection pools and bounded worker pools for asynchronous callbacks.
- Use an outbox/inbox pattern for reliable event delivery.
- Move to a Saga when inventory, payment, and order services become separate
  deployables.

## Trade-off

A modular monolith gives strong local ACID guarantees and is simpler to reason
about. The boundary is the external payment provider: it requires idempotency
and compensation rather than pretending that one distributed ACID transaction
exists.
