# Payment Gateway LLD Design

## Scope

The implementation supports multiple clients, payment methods, bank integrations,
weighted traffic routing, method-specific validation, and simulated bank outcomes.

Implementation root:

`src\LLD\PaymentGateway`

## Object model

```text
PaymentGatewayService
    |
    +--> PaymentRouter ---> Bank
    |
    +--> Client registry

PaymentRequest ---> PaymentDetails
                       +--> CardDetails
                       +--> UpiDetails
                       +--> NetBankingDetails
```

### `PaymentGatewayService`

The application facade and entry point. It owns client onboarding and delegates
bank selection to `PaymentRouter`.

### `PaymentRouter`

Owns the routing policy for each `PaymentMethod`. It selects a bank using
configured percentages whose total must equal 100.

### `Bank`

An abstraction for a bank integration. Adding a new bank requires a new
implementation, not changes to the gateway service.

### `PaymentDetails`

Polymorphic contract for method-specific credentials. Each implementation
encapsulates validation for its own payment method.

### `PaymentRequest`

Immutable payment command containing transaction ID, amount, currency, and
validated method details.

### `PaymentResult`

Immutable result containing transaction ID, selected bank, status, and message.

## SOLID principles

### Single Responsibility Principle

- `PaymentGatewayService` handles gateway use cases.
- `PaymentRouter` handles traffic distribution.
- `CardDetails`, `UpiDetails`, and `NetBankingDetails` validate their own data.
- `SimulatedBank` handles bank-response simulation.

Changing routing percentages does not require changing credential validation.

### Open/Closed Principle

The system is open for:

- New payment methods through `PaymentDetails`.
- New banks through `Bank`.
- New bank-selection policies through a future router strategy.

Existing orchestration does not need modification for every new bank.

### Liskov Substitution Principle

Every `Bank` implementation can be passed to `PaymentRouter` and return a
`PaymentResult`. The router does not depend on bank-specific behavior.

### Interface Segregation Principle

`Bank` and `PaymentDetails` are small focused interfaces. Implementations are not
forced to depend on unrelated operations.

### Dependency Inversion Principle

`PaymentGatewayService` depends on the `PaymentRouter` abstraction boundary, and
the router depends on the `Bank` interface rather than concrete bank classes.

## Design patterns

### Strategy-like polymorphism

`PaymentDetails` represents different payment-detail strategies. Each payment
method owns its validation and data shape.

This avoids a large conditional block such as:

```text
if card ...
else if UPI ...
else if net banking ...
```

### Adapter pattern

Concrete `Bank` implementations act as adapters around external bank APIs. The
gateway sees one stable `Bank.process()` contract even when real providers have
different SDKs and response formats.

### Facade pattern

`PaymentGatewayService` provides a small client-facing facade:

- `onboardClient()`
- `makePayment()`
- `showDistribution()`

Clients do not need to coordinate routing and bank integration themselves.

### Value objects

`PaymentRequest`, payment details, and `PaymentResult` are immutable value-like
objects. This prevents callers from mutating payment data after validation.

## Routing design

Each payment method has an independent distribution:

```text
CARD        -> HDFC 100%
NET_BANKING -> ICICI 100%
UPI         -> HDFC 30%, SBI 70%
```

The router generates a value from 1 to 100 and selects the first cumulative
percentage containing that value. Invalid distributions are rejected unless they
sum exactly to 100.

This policy can later be replaced with:

- Round robin
- Least-loaded bank
- Health-aware routing
- Region-aware routing
- Cost-aware routing

## Concurrency and state

The in-memory registry and routing configuration methods are synchronized. This
keeps updates and reads consistent within one JVM.

For production:

- Store client and routing configuration durably.
- Use an atomic configuration snapshot rather than long locks.
- Add bank health checks and circuit breakers.
- Use idempotency keys for retries.
- Never log raw card numbers, CVVs, passwords, or other secrets.
- Encrypt sensitive data and prefer tokenized provider references.

## Error handling

The implementation rejects:

- Unknown clients
- Missing payment details
- Invalid card, VPA, username, or password values
- Unregistered banks
- Distributions that do not total 100
- Missing routing configuration

The simulated bank returns success or failure randomly. A production bank adapter
should map provider errors into stable domain results and preserve the original
transaction ID.

## Testing

`PaymentGatewayServiceTest` verifies:

- Successful routing to a configured bank
- Unknown-client rejection
- Card validation
- Invalid distribution rejection

Additional production tests should cover routing percentages statistically,
duplicate transactions, bank timeouts, retries, and concurrent configuration
updates.
