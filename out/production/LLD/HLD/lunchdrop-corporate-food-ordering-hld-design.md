# LunchDrop — Corporate Workplace Food Ordering HLD

## 1. Problem Statement

Design **LunchDrop**, an application used by corporate employees to order food at their workplace.

The application shows a list of restaurants and menus. Available restaurants rotate daily. Employees can browse menus, customize food items, and place orders.

Example:

- A company office has 5 restaurants available today.
- An employee opens LunchDrop.
- They browse a taco restaurant.
- They customize tacos with vegetables, toppings, sauces, and sides.
- They place an order before the cutoff time.
- LunchDrop aggregates workplace orders and sends them to restaurants.

---

## 2. Clarified Scope

LunchDrop is not a general instant food delivery app like DoorDash. It is a **scheduled corporate lunch ordering platform**.

Core assumption:

> Restaurants rotate daily per office, employees order before a cutoff time, and LunchDrop batches orders for workplace delivery.

---

## 3. Functional Requirements

### 3.1 Employee-facing

1. Users can log in.
2. Users can view restaurants available for their office on a given day.
3. Users can browse restaurant menus.
4. Users can view customization options for menu items.
5. Users can select items and customizations.
6. Users can place orders.
7. Users can view order status.
8. Users can view order history.

### 3.2 Admin-facing

1. LunchDrop admins can onboard companies and offices.
2. Admins can onboard restaurants.
3. Admins can configure restaurant menus.
4. Admins can define menu item customization groups.
5. Admins can schedule daily restaurant rotation per office.
6. Admins can define cutoff time and delivery time per office.

### 3.3 Restaurant / operations-facing

1. Restaurants can receive grouped orders.
2. Restaurants can confirm order acceptance.
3. Restaurants can update fulfillment status.
4. LunchDrop operations can track office-level batches.

---

## 4. Non-Functional Requirements

| Requirement | Target |
|---|---|
| Availability | High availability during lunch ordering windows |
| Latency | Restaurant/menu browse under 200 ms p95 from cache |
| Order placement | Strong correctness; avoid duplicate orders |
| Scalability | Support small offices to national corporate usage |
| Consistency | Strong consistency for order placement; eventual consistency for menu/admin updates |
| Fault tolerance | Restaurant dispatch, notification, and analytics failures should not block order creation |
| Observability | Monitor order failures, cutoff violations, menu errors, payment failures, and dispatch delays |

---

## 5. Out of Scope

For the first version:

- Real-time delivery-driver tracking.
- Complex refunds.
- Loyalty programs.
- Personalized recommendations.
- Deep restaurant POS integration.
- Marketplace-style restaurant discovery.

These can be added later.

---

## 6. Scale Assumptions

We design from small corporate scale to large national scale.

### 6.1 Small scale

| Metric | Assumption |
|---|---:|
| Companies | 10-50 |
| Offices | 50-200 |
| Users | 10K-50K |
| Daily orders | 2K-10K |
| Restaurants per office/day | 3-10 |
| Peak ordering window | 1-2 hours |

At this scale, a modular monolith with PostgreSQL is enough.

### 6.2 Large scale

| Metric | Assumption |
|---|---:|
| Companies | 5,000 |
| Offices | 50,000 |
| Employees | 10M |
| DAU | 2M |
| Daily orders | 500K-1M |
| Peak ordering window | 2 hours |
| Peak order QPS | 70-150 QPS average during peak, higher bursts |
| Browse QPS | 5K-20K QPS |
| Restaurants per office/day | 5-20 |
| Menu items per restaurant | 50-200 |

Lunch ordering traffic is bursty. Most users browse and order between morning and lunch cutoff.

Important observation:

> Browse traffic is much higher than order traffic, but order placement requires stronger correctness.

---

## 7. High-Level Requirements Reasoning

### 7.1 Why office-based availability?

Available restaurants are not global. They depend on:

- Company.
- Office.
- Date.
- Delivery zone.
- Restaurant schedule.
- Cutoff time.
- Operational capacity.

So the primary read pattern is:

```text
officeId + date -> available restaurants
```

Using only latitude/longitude is weaker because corporate users are usually tied to an office.

### 7.2 Why strong consistency for orders?

Order placement must avoid:

- Duplicate orders from retry.
- Orders after cutoff.
- Ordering unavailable items.
- Invalid customization combinations.
- Overbooking restaurant capacity if capacity limits exist.

So order creation should be transactional.

### 7.3 Why eventual consistency for menus?

Menu updates are less frequent and can tolerate short propagation delay. If a menu item changes, cache invalidation or TTL-based refresh is acceptable as long as order placement revalidates against authoritative data.

---

## 8. API Design

### 8.1 Get today's restaurants for office

```http
GET /v1/offices/{officeId}/restaurants?date=2026-10-09
Authorization: Bearer <token>
```

Response:

```json
{
  "officeId": "office_123",
  "date": "2026-10-09",
  "cutoffTime": "2026-10-09T10:30:00+05:30",
  "deliveryTime": "2026-10-09T12:30:00+05:30",
  "restaurants": [
    {
      "restaurantId": "rest_456",
      "name": "Taco House",
      "cuisine": "Mexican",
      "imageUrl": "https://cdn.example.com/restaurants/rest_456.png",
      "status": "AVAILABLE",
      "minOrderAmount": 0
    }
  ]
}
```

Notes:

- User must be authorized for the office.
- Result is highly cacheable.
- Cache key:

```text
available_restaurants:{officeId}:{date}
```

### 8.2 Get restaurant menu

```http
GET /v1/restaurants/{restaurantId}/menus?officeId=office_123&date=2026-10-09
Authorization: Bearer <token>
```

Response:

```json
{
  "restaurantId": "rest_456",
  "date": "2026-10-09",
  "menuVersion": 17,
  "items": [
    {
      "menuItemId": "item_789",
      "name": "Veg Taco",
      "description": "Soft taco with vegetables and sauce",
      "price": 650,
      "currency": "INR",
      "available": true,
      "customizationGroups": [
        {
          "groupId": "toppings",
          "name": "Toppings",
          "minSelections": 0,
          "maxSelections": 3,
          "required": false,
          "options": [
            {
              "optionId": "lettuce",
              "name": "Lettuce",
              "priceDelta": 0
            },
            {
              "optionId": "jalapeno",
              "name": "Jalapeno",
              "priceDelta": 50
            }
          ]
        },
        {
          "groupId": "sauce",
          "name": "Sauce",
          "minSelections": 1,
          "maxSelections": 1,
          "required": true,
          "options": [
            {
              "optionId": "chipotle_mayo",
              "name": "Chipotle Mayo",
              "priceDelta": 0
            }
          ]
        }
      ]
    }
  ]
}
```

Notes:

- Include `menuVersion` to detect stale clients.
- Menu is cacheable but must be revalidated during order placement.

### 8.3 Place order

```http
POST /v1/orders
Authorization: Bearer <token>
Idempotency-Key: client_generated_uuid
```

Request:

```json
{
  "officeId": "office_123",
  "restaurantId": "rest_456",
  "deliveryDate": "2026-10-09",
  "menuVersion": 17,
  "items": [
    {
      "menuItemId": "item_789",
      "quantity": 1,
      "customizations": [
        {
          "groupId": "toppings",
          "optionIds": ["lettuce", "jalapeno"]
        },
        {
          "groupId": "sauce",
          "optionIds": ["chipotle_mayo"]
        }
      ]
    }
  ]
}
```

Response:

```json
{
  "orderId": "order_abc",
  "status": "PLACED",
  "totalAmount": 700,
  "currency": "INR",
  "estimatedDeliveryTime": "2026-10-09T12:30:00+05:30"
}
```

Important:

- `POST`, not `GET`, because order placement creates state.
- `Idempotency-Key` prevents duplicate orders when clients retry.
- Server recalculates price. Client-provided price should never be trusted.

Possible errors:

```text
401 UNAUTHENTICATED
403 USER_NOT_ALLOWED_FOR_OFFICE
409 DUPLICATE_IDEMPOTENCY_KEY
409 ORDERING_CUTOFF_PASSED
409 RESTAURANT_NOT_AVAILABLE_TODAY
409 MENU_ITEM_UNAVAILABLE
422 INVALID_CUSTOMIZATION
422 CUSTOMIZATION_REQUIRED
422 MAX_SELECTION_EXCEEDED
```

### 8.4 Get order status

```http
GET /v1/orders/{orderId}
Authorization: Bearer <token>
```

Response:

```json
{
  "orderId": "order_abc",
  "officeId": "office_123",
  "restaurantId": "rest_456",
  "status": "CONFIRMED_BY_RESTAURANT",
  "items": [],
  "createdAt": "2026-10-09T09:15:00+05:30",
  "deliveryTime": "2026-10-09T12:30:00+05:30"
}
```

### 8.5 Get user orders

```http
GET /v1/users/me/orders?from=2026-10-01&to=2026-10-09&limit=20&cursor=abc
```

Use cursor pagination for order history.

---

## 9. Data Model

PostgreSQL is a strong fit for the core transactional system because:

- Orders require transactions.
- Data has relational structure.
- Queries often join users, offices, restaurants, schedules, and orders.
- Strong consistency is needed for order placement.
- Early scale does not justify operational complexity of NoSQL.

At large scale, partition/shard core tables by region or office.

### 9.1 Company

```text
companies
---------
id
name
status
created_at
updated_at
```

### 9.2 Office

```text
offices
-------
id
company_id
name
address
latitude
longitude
timezone
status
created_at
updated_at
```

Indexes:

```text
(company_id)
(latitude, longitude) if geo lookup is needed
```

### 9.3 User

```text
users
-----
id
company_id
office_id
email
name
role
status
created_at
updated_at
```

Indexes:

```text
unique(email)
(company_id, office_id)
```

### 9.4 Restaurant

```text
restaurants
-----------
id
name
cuisine
status
contact_info
created_at
updated_at
```

### 9.5 Restaurant service area

```text
restaurant_service_areas
------------------------
id
restaurant_id
office_id
active
```

This maps restaurants to offices they can serve.

### 9.6 Menu

```text
menus
-----
id
restaurant_id
name
version
status
effective_from
effective_to
created_at
updated_at
```

### 9.7 Menu item

```text
menu_items
----------
id
menu_id
restaurant_id
name
description
base_price
currency
available
created_at
updated_at
```

Indexes:

```text
(restaurant_id, available)
(menu_id)
```

### 9.8 Customization group

```text
customization_groups
--------------------
id
menu_item_id
name
required
min_selections
max_selections
sort_order
```

Example:

```text
Sauce: required, min=1, max=1
Toppings: optional, min=0, max=3
```

### 9.9 Customization option

```text
customization_options
---------------------
id
group_id
name
price_delta
available
sort_order
```

### 9.10 Daily restaurant schedule

```text
daily_restaurant_schedules
--------------------------
id
office_id
restaurant_id
date
menu_id
cutoff_time
delivery_time
status
capacity_limit
created_at
updated_at
```

Indexes:

```text
unique(office_id, restaurant_id, date)
(office_id, date, status)
(restaurant_id, date)
```

This table powers:

```text
officeId + date -> available restaurants
```

### 9.11 Order

```text
orders
------
id
user_id
company_id
office_id
restaurant_id
schedule_id
delivery_date
status
subtotal_amount
tax_amount
total_amount
currency
idempotency_key
created_at
updated_at
```

Indexes:

```text
unique(user_id, idempotency_key)
(user_id, created_at desc)
(office_id, delivery_date)
(restaurant_id, delivery_date)
(schedule_id, status)
```

### 9.12 Order item

```text
order_items
-----------
id
order_id
menu_item_id
name_snapshot
quantity
base_price_snapshot
total_price
```

Store snapshots because menu names/prices can change later.

### 9.13 Order item customization

```text
order_item_customizations
-------------------------
id
order_item_id
group_id
group_name_snapshot
option_id
option_name_snapshot
price_delta_snapshot
```

### 9.14 Office restaurant batch

```text
office_restaurant_batches
-------------------------
id
office_id
restaurant_id
delivery_date
schedule_id
status
order_count
total_amount
dispatched_at
confirmed_at
created_at
updated_at
```

This represents grouped orders sent to one restaurant for one office/date.

---

## 10. High-Level Architecture

### 10.1 Small-scale architecture

```text
Client Apps
   |
   v
Load Balancer
   |
   v
LunchDrop Modular Monolith
   |-- Auth Module
   |-- Restaurant/Menu Module
   |-- Schedule Module
   |-- Order Module
   |-- Admin Module
   |-- Notification Module
   |
   +--> PostgreSQL
   +--> Redis
   +--> Email/SMS Provider
```

This is enough for early scale.

Benefits:

- Simpler deployment.
- Easier transactions.
- Lower operational overhead.
- Faster product iteration.

Limitations:

- Harder to scale teams independently.
- Background jobs and APIs compete for same resources.
- Large traffic spikes can impact the whole application.

### 10.2 Large-scale architecture

![LunchDrop large-scale architecture](../../docs/lunchdrop-large-scale-architecture.png)

Source: `docs/lunchdrop-large-scale-architecture.png`

```text
Web / Mobile Clients
        |
        v
API Gateway / Load Balancer
        |
        +-------------------+
        |                   |
        v                   v
 Auth Service        LunchDrop APIs
                            |
        +-------------------+-------------------+
        |                   |                   |
        v                   v                   v
 Restaurant/Menu      Schedule Service      Order Service
 Service                   |                   |
        |                  |                   |
        v                  v                   v
 Menu DB/Cache       Schedule DB/Cache      Order DB
                                                |
                                                v
                                      Message Broker / Event Bus
                                                |
              +---------------------------------+-----------------------------+
              |                                 |                             |
              v                                 v                             v
     Batch Dispatch Service             Notification Service          Analytics Pipeline
              |                                 |                             |
              v                                 v                             v
       Restaurant Portal/API             Email/SMS/Push                Data Lake/Warehouse
```

### 10.3 Component responsibilities

| Component | Responsibility |
|---|---|
| API Gateway | Routing, auth enforcement, rate limiting |
| Auth Service | Login, JWT/session validation, user-office mapping |
| Restaurant/Menu Service | Restaurants, menus, items, customizations |
| Schedule Service | Daily restaurant rotation, cutoff, delivery windows |
| Order Service | Validate and create orders transactionally |
| Payment/Billing Service | Optional payment or company billing |
| Batch Dispatch Service | Group office orders and send to restaurants |
| Notification Service | Order confirmation, reminders, delivery updates |
| Analytics Pipeline | Historical order and menu analytics |
| Admin Portal | Restaurant, company, office, schedule, and menu management |

---

## 11. Complete User Flow

### 11.1 Login flow

```text
Employee opens LunchDrop
  -> enters SSO/email credentials
  -> Auth Service validates user
  -> returns token with userId, companyId, officeId, roles
```

Token claims:

```json
{
  "userId": "user_123",
  "companyId": "company_456",
  "officeId": "office_789",
  "roles": ["EMPLOYEE"]
}
```

Why include `officeId`?

- Most LunchDrop reads are scoped to office.
- Reduces repeated lookup.
- Authorization becomes simpler.

Still validate important operations server-side.

---

### 11.2 View daily restaurants flow

```text
Client
  -> GET /v1/offices/{officeId}/restaurants?date=today
API Gateway
  -> validates token
Restaurant/Schedule API
  -> checks Redis cache
  -> if cache miss, queries Schedule DB
  -> returns restaurants and cutoff time
```

Cache:

```text
available_restaurants:{officeId}:{date}
```

TTL:

```text
5-30 minutes
```

Invalidation:

- Admin schedule update.
- Restaurant disabled.
- Capacity reached.
- Cutoff passed.

Important:

> Cache improves read performance, but order placement must still validate against the database.

---

### 11.3 Browse menu flow

```text
Client
  -> GET /v1/restaurants/{restaurantId}/menus?officeId=office_123&date=today
API Gateway
  -> validates token
Menu Service
  -> verifies restaurant is scheduled for office/date
  -> reads menu from Redis/cache
  -> if cache miss, reads Menu DB
  -> returns items and customization groups
```

Cache:

```text
menu:{restaurantId}:{menuId}:{version}
```

Why menu version?

- Menu cache can be long-lived.
- Updates create a new version.
- Clients can send `menuVersion` during order placement.

---

### 11.4 Customize item flow

The client uses menu metadata:

```text
Customization group:
  required=true
  minSelections=1
  maxSelections=1

Customization option:
  available=true
  priceDelta=50
```

Client-side validation improves UX, but server must validate:

- Required groups.
- Min/max selections.
- Option belongs to group.
- Group belongs to item.
- Option is available.
- Price delta is current.

---

### 11.5 Place order flow

![LunchDrop order placement and batch dispatch flow](../../docs/lunchdrop-order-placement-flow.png)

Source: `docs/lunchdrop-order-placement-flow.png`

The diagram shows the complete lifecycle from the moment an employee clicks **Place Order** until the order becomes part of a restaurant batch.

There are two important parts:

1. **Synchronous order creation path**
   - This path must complete before the user sees order confirmation.
   - It validates the request, creates the order, and returns `orderId`.
   - It must be strongly consistent because the user should not receive a fake confirmation.

2. **Asynchronous post-order path**
   - This path happens after the order is safely stored.
   - It sends notifications, updates analytics, handles billing, and dispatches grouped orders to restaurants.
   - These tasks should not block the user-facing order placement API.

Detailed explanation:

```text
1. Employee selects food
   The user chooses a restaurant, menu item, quantity, and customizations.
   Example: Veg Taco + lettuce + jalapeno + chipotle mayo.

2. Client sends POST /orders
   The request includes officeId, restaurantId, deliveryDate, items,
   customizations, menuVersion, and Idempotency-Key.

3. API Gateway validates authentication
   It checks the user token and forwards the request to Order Service.

4. Order Service starts a DB transaction
   Order placement needs strong consistency, so validation and writes happen
   inside a transaction.

5. Idempotency check
   If the same request was already processed, return the existing order.
   This prevents duplicate orders when the user retries or taps twice.

6. Validate office and restaurant schedule
   The service verifies that the user belongs to the office and that the
   restaurant is available for that office on that date.

7. Validate cutoff time
   The service checks server-side time against the office's cutoff time.
   Client time is never trusted.

8. Validate menu item and customizations
   The service verifies item availability, required customization groups,
   min/max selections, and option validity.

9. Recalculate price
   The server calculates the final price from current menu data.
   Client-provided price is ignored.

10. Persist order
    The service stores order, order items, customization snapshots, and an
    outbox event in the same transaction.

11. Commit transaction
    Once committed, the order is durable and the user can safely receive
    confirmation.

12. Outbox relay publishes OrderPlaced
    A background worker reads the outbox table and publishes the event to the
    message broker.

13. Downstream consumers process the order
    Notification, billing, analytics, and batch dispatch consume the event.

14. Batch dispatch runs at cutoff
    Orders are grouped by officeId + restaurantId + deliveryDate and sent to
    the restaurant as one consolidated batch.
```

Key design idea:

> The user-facing order creation path is synchronous and strongly validated. Everything that can happen later should be asynchronous and idempotent.

```text
Client
  -> POST /v1/orders with Idempotency-Key
API Gateway
  -> validates token
Order Service
  -> starts DB transaction
  -> checks idempotency key
  -> validates user belongs to office
  -> validates restaurant scheduled for office/date
  -> checks cutoff time
  -> validates item availability
  -> validates customizations
  -> recalculates price
  -> creates order
  -> creates order items and customization snapshots
  -> commits transaction
  -> publishes OrderPlaced event
  -> returns orderId
```

Order placement transaction:

```text
BEGIN

1. SELECT existing order by (user_id, idempotency_key)
   - if exists, return existing response

2. SELECT schedule FOR UPDATE or equivalent concurrency control
   - validate schedule status
   - validate cutoff
   - validate capacity if capacity_limit exists

3. Validate menu item and customization options

4. Insert order
5. Insert order_items
6. Insert order_item_customizations
7. Update schedule/order counters if needed

COMMIT

8. Publish OrderPlaced event
```

Why publish event after commit?

- Avoid downstream services processing an order that does not exist.

Better production pattern:

> Use the transactional outbox pattern so the order DB commit and event creation are atomic.

---

### 11.6 Order event flow

After order creation:

```text
Order Service
  -> writes order
  -> writes outbox event
Outbox Relay
  -> publishes OrderPlaced to Kafka/SQS
Consumers:
  -> Notification Service
  -> Batch Dispatch Service
  -> Analytics Pipeline
  -> Billing Service
```

Event:

```json
{
  "eventId": "evt_123",
  "eventType": "OrderPlaced",
  "orderId": "order_abc",
  "userId": "user_123",
  "officeId": "office_789",
  "restaurantId": "rest_456",
  "deliveryDate": "2026-10-09",
  "createdAt": "2026-10-09T09:20:00+05:30"
}
```

Consumers must be idempotent using `eventId` or `orderId`.

---

### 11.7 Restaurant batch dispatch flow

LunchDrop usually sends grouped orders to restaurants.

```text
Cutoff time reached
  -> Batch Dispatch Scheduler triggers per office/restaurant/date
  -> reads all PLACED orders for schedule
  -> creates or updates office_restaurant_batch
  -> sends grouped order to restaurant portal/API/email
  -> marks batch DISPATCHED
  -> restaurants confirm
  -> marks related orders CONFIRMED_BY_RESTAURANT
```

Batch key:

```text
officeId + restaurantId + deliveryDate
```

Why batch?

- Restaurant preparation is easier.
- Delivery logistics are simpler.
- Fewer external calls.
- Corporate lunch is scheduled, not instant.

---

### 11.8 Order status flow

Possible order states:

```text
PLACED
CONFIRMED_BY_RESTAURANT
PREPARING
READY_FOR_PICKUP
OUT_FOR_DELIVERY
DELIVERED
CANCELLED
FAILED
```

For MVP:

```text
PLACED -> CONFIRMED_BY_RESTAURANT -> DELIVERED
```

Order status updates come from:

- Restaurant portal.
- Operations dashboard.
- Batch Dispatch Service.
- Delivery integration in future.

---

## 12. Daily Restaurant Rotation

### 12.1 Rotation model

The daily restaurant list is generated from:

- Office.
- Restaurant service area.
- Restaurant availability.
- Company preferences.
- Cuisine diversity.
- Contract rules.
- Operational capacity.

For MVP, admins can configure schedules manually:

```text
office_123, 2026-10-09 -> rest_1, rest_2, rest_3
```

For scale, use a scheduler:

```text
Restaurant Rotation Scheduler
  -> runs daily for future dates
  -> selects restaurants per office/date
  -> writes daily_restaurant_schedules
  -> warms Redis cache
```

### 12.2 Rotation scheduler flow

```text
Every day at 00:00 local office timezone
  -> find offices needing schedule
  -> find eligible restaurants
  -> apply rotation rules
  -> write daily_restaurant_schedules
  -> publish ScheduleCreated events
  -> warm cache
```

Important:

> The scheduler should be idempotent because retries may happen.

Unique constraint:

```text
unique(office_id, restaurant_id, date)
```

prevents duplicate schedules.

---

## 13. Caching Strategy

### 13.1 What to cache

| Data | Cache key | Reason |
|---|---|---|
| Daily restaurants | `available_restaurants:{officeId}:{date}` | Very hot read |
| Menus | `menu:{restaurantId}:{menuId}:{version}` | Reused by many users |
| User office mapping | `user_context:{userId}` | Frequent auth context |
| Cutoff/schedule | `schedule:{officeId}:{restaurantId}:{date}` | Needed for order validation |

### 13.2 Cache correctness

Cache is not source of truth.

Order placement must always validate:

- Schedule is active.
- Cutoff has not passed.
- Menu item is available.
- Customization is valid.
- Price is recalculated.

### 13.3 Cache invalidation

Invalidation events:

- Menu updated.
- Restaurant disabled.
- Schedule changed.
- Cutoff passed.
- Item sold out.

Possible strategies:

1. TTL-based cache.
2. Event-based invalidation.
3. Versioned cache keys.

Recommended:

```text
Menus: versioned keys
Daily restaurant list: TTL + explicit invalidation
Order validation: DB-backed
```

---

## 14. Storage Choices

### 14.1 PostgreSQL for transactional data

Use PostgreSQL for:

- Users.
- Companies.
- Offices.
- Restaurants.
- Menus.
- Schedules.
- Orders.
- Order items.

Reason:

- Relational data.
- Transactions.
- Strong consistency.
- Indexes and constraints.
- Easier operational model.

### 14.2 Redis for cache

Use Redis for:

- Daily restaurants.
- Menu cache.
- User-office context.
- Hot schedule lookup.

Reason:

- Low-latency reads.
- High read QPS.
- Data can be regenerated.

Trade-off:

- Cache invalidation complexity.
- Redis failure should degrade performance, not correctness.

### 14.3 Message broker

Use Kafka/SQS/RabbitMQ depending on scale.

At small/medium scale:

```text
SQS/RabbitMQ is enough for background jobs
```

At large scale:

```text
Kafka is useful because multiple consumers need durable order events:
  - notification
  - dispatch
  - analytics
  - billing
  - auditing
```

Do not introduce Kafka only because it is popular. It is justified if:

- Multiple independent consumers exist.
- Events need durable replay.
- High event throughput is expected.
- Producers should not block on downstream services.

### 14.4 Data warehouse / lake

Historical analytics:

- Daily order volume.
- Restaurant popularity.
- Office-level demand.
- Menu item popularity.
- Cancellation/failure rates.
- Revenue and billing reports.

Pipeline:

```text
Order events -> Kafka -> Stream/batch ingestion -> Data Lake/Warehouse
```

---

## 15. Consistency Model

| Area | Consistency |
|---|---|
| Order creation | Strong consistency |
| Idempotency | Strong consistency |
| Cutoff validation | Strong consistency |
| Restaurant availability display | Eventual consistency acceptable |
| Menu browsing | Eventual consistency acceptable |
| Notifications | Eventual consistency |
| Analytics | Eventual consistency |
| Batch dispatch | Eventually consistent but must be idempotent |

Senior-level answer:

> Browsing can tolerate slightly stale data because final validation happens during order placement. Order creation itself requires strong consistency because we must avoid duplicate orders, cutoff violations, invalid menu selections, and incorrect pricing.

---

## 16. Idempotency and Duplicate Handling

### 16.1 Duplicate order request

Causes:

- Client timeout.
- User taps order button twice.
- Mobile retry.
- API gateway retry.

Solution:

```text
unique(user_id, idempotency_key)
```

Flow:

```text
If request with same idempotency key already succeeded:
  return existing order

If same key is in progress:
  return 409 or wait briefly

If same key has different payload:
  return 422 IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD
```

### 16.2 Duplicate events

Consumers maintain processed event table:

```text
processed_events
----------------
event_id
consumer_name
processed_at
```

or use natural idempotency:

```text
unique(batch_id, order_id)
```

---

## 17. Partitioning and Scaling

### 17.1 Read scaling

Restaurant/menu reads are scaled by:

- CDN for images.
- Redis cache for menus and restaurant lists.
- Read replicas for DB.
- Precomputed daily schedule.
- Cache warming before ordering window.

### 17.2 Write scaling

Order writes are scaled by:

- Partitioning by region or office.
- Keeping order transaction short.
- Avoiding synchronous notification/dispatch calls.
- Using outbox + async event processing.

### 17.3 Database partitioning

Good partition candidates:

```text
orders partitioned by delivery_date
orders sharded by region/office_id at very large scale
```

For national scale:

```text
region -> DB cluster
officeId -> shard
```

Reason:

- Most queries are office/date scoped.
- Operational teams work by region.
- Lunch traffic is region/timezone dependent.

### 17.4 Hot office problem

A very large corporate campus may have thousands of users ordering at once.

Mitigations:

- Cache daily restaurants and menus.
- Rate-limit expensive endpoints.
- Use DB indexes on `(office_id, delivery_date)`.
- Keep order placement transaction minimal.
- Use asynchronous batch dispatch.
- Pre-warm cache for large offices.

---

## 18. Failure Scenarios

### 18.1 Redis unavailable

Impact:

- Restaurant/menu browsing becomes slower.

Handling:

- Fall back to DB/read replicas.
- Use local in-process cache for hottest menus.
- Alert on cache outage.
- Order correctness unaffected because DB is source of truth.

### 18.2 Order DB unavailable

Impact:

- Cannot place orders.

Handling:

- Return clear error.
- Do not accept orders into a queue unless business accepts delayed confirmation.
- Show user that ordering is temporarily unavailable.
- Restore from replica/failover.

Reason:

> Order placement needs strong confirmation. A queued order without validation can violate cutoff/menu/capacity rules.

### 18.3 Message broker unavailable

Impact:

- Order creation can still succeed if using transactional outbox.
- Notifications and dispatch may be delayed.

Handling:

- Persist outbox events in Order DB.
- Outbox relay retries publishing.
- Alert on outbox backlog.

### 18.4 Notification provider down

Impact:

- Users may not receive confirmation notification.

Handling:

- Order status remains available via API.
- Retry notifications asynchronously.
- Use secondary provider if needed.

### 18.5 Restaurant dispatch fails

Impact:

- Restaurant may not receive grouped order.

Handling:

- Retry dispatch.
- Alert operations team.
- Provide manual export from admin portal.
- Track batch status.

### 18.6 Duplicate order event

Impact:

- Duplicate notification or duplicate restaurant dispatch.

Handling:

- Idempotent consumers.
- Unique constraints in batch-order mapping.
- Deduplicate by `eventId` or `orderId`.

### 18.7 Cutoff race condition

Scenario:

```text
User submits order exactly near cutoff time.
```

Handling:

- Use server time, not client time.
- Compare against office timezone cutoff converted to UTC.
- Validate cutoff inside the order transaction.

### 18.8 Menu updated while user is ordering

Scenario:

```text
User opened menu version 17.
Admin updates menu to version 18.
User submits old version.
```

Handling:

- Server validates latest active menu.
- If item/options still valid, accept and price using latest data.
- If invalid, return `MENU_VERSION_STALE` or `MENU_ITEM_UNAVAILABLE`.

---

## 19. Payment and Billing Options

LunchDrop may support different payment models.

### 19.1 Company-paid lunch

Company pays invoice.

Order flow:

```text
Place order -> record company billing entry -> invoice later
```

Simpler for corporate use.

### 19.2 Employee-paid lunch

Employee pays per order.

Order flow:

```text
Create payment intent
  -> payment succeeds
  -> create order
```

or:

```text
Create pending order
  -> authorize payment
  -> confirm order
```

Payment introduces distributed transaction complexity.

Recommended for MVP:

> Start with company-paid or payroll-deducted billing if possible. Add per-user payments later with a payment state machine.

---

## 20. Security

### 20.1 Authentication

Options:

- Company SSO.
- Email/password.
- Magic link.

For corporate employees, SSO is preferred.

### 20.2 Authorization

Enforce:

- User can only access their company/office.
- Admins can manage only authorized companies/offices.
- Restaurant users can access only their restaurant orders.

### 20.3 Data protection

- Encrypt data in transit.
- Encrypt sensitive data at rest.
- Avoid storing unnecessary payment data.
- Audit admin changes to menus/schedules.

### 20.4 Abuse prevention

- Rate-limit order placement.
- Prevent repeated failed order attempts.
- Validate all customizations server-side.
- Protect admin APIs with stronger controls.

---

## 21. Observability

### 21.1 Metrics

Business metrics:

```text
orders_created
orders_failed
orders_cancelled
daily_orders_per_office
orders_per_restaurant
average_order_value
cutoff_rejection_count
menu_unavailable_count
```

System metrics:

```text
api_latency_p50_p95_p99
order_creation_latency
db_connection_pool_usage
cache_hit_rate
broker_lag
outbox_backlog
dispatch_retry_count
notification_failure_count
```

### 21.2 Logs

Log:

- Order creation decision.
- Validation failure reason.
- Idempotency key usage.
- Dispatch attempts.
- Admin schedule/menu changes.

Do not log:

- Sensitive tokens.
- Payment credentials.
- Full PII unnecessarily.

### 21.3 Tracing

Trace critical path:

```text
POST /orders
  -> auth
  -> schedule validation
  -> menu validation
  -> price calculation
  -> DB transaction
  -> outbox write
```

### 21.4 Alerts

Alert on:

- High order failure rate.
- Order creation latency above SLO.
- DB saturation.
- Redis failure.
- Outbox backlog.
- Dispatch backlog near restaurant preparation time.
- Notification failure spike.

---

## 22. Disaster Recovery and Deployment

### 22.1 Backup and recovery

- Regular PostgreSQL backups.
- Point-in-time recovery.
- Cross-region replicas for critical data.
- Restore drills.

### 22.2 Multi-region

For large national scale:

```text
Region-specific offices served by regional stack
```

Example:

```text
US-East offices -> US-East services/database
US-West offices -> US-West services/database
India offices -> India services/database
```

Why regional?

- Lower latency.
- Timezone-specific lunch peaks.
- Fault isolation.
- Data residency if needed.

### 22.3 Zero-downtime deployments

Use:

- Backward-compatible schema changes.
- Blue/green or canary deployments.
- Feature flags.
- Rolling deployments.
- Consumer version compatibility for events.

---

## 23. Trade-Offs

### 23.1 Modular monolith vs microservices

| Option | Pros | Cons |
|---|---|---|
| Modular monolith | Simple, fast to build, easy transactions | Harder to scale teams/components later |
| Microservices | Independent scaling and ownership | More operational complexity |

Recommendation:

```text
Small/medium scale: modular monolith
Large scale: split order, menu, schedule, dispatch, notification services
```

### 23.2 PostgreSQL vs NoSQL

PostgreSQL is preferred for core transactional data because:

- Strong transactions.
- Relational model.
- Constraints.
- Idempotency uniqueness.
- Order correctness.

NoSQL may be useful later for:

- High-volume analytics.
- Denormalized read models.
- Event storage.

### 23.3 Synchronous vs asynchronous order processing

Order creation should be synchronous:

```text
User must know whether order is placed.
```

Downstream work should be asynchronous:

```text
notifications
analytics
restaurant dispatch
billing exports
```

### 23.4 Cache vs correctness

Cache improves browse latency but must not decide order correctness.

Correct pattern:

```text
Browse from cache
Validate order from source of truth
```

---

## 24. Final Architecture Summary

```text
Employees
   |
   v
Web/Mobile Client
   |
   v
API Gateway
   |
   +-----------------+------------------+------------------+
   |                 |                  |                  |
   v                 v                  v                  v
Auth Service   Schedule Service   Menu Service       Order Service
                     |                 |                  |
                     v                 v                  v
                Schedule DB       Menu DB/Cache       Order DB
                                                         |
                                                         v
                                                   Outbox Table
                                                         |
                                                         v
                                                Message Broker
                                                         |
        +------------------------+-----------------------+------------------+
        |                        |                       |                  |
        v                        v                       v                  v
Notification Service     Batch Dispatch Service    Billing Service    Analytics Pipeline
        |                        |                       |                  |
        v                        v                       v                  v
Email/SMS/Push          Restaurant Portal/API      Invoice/Payment     Data Warehouse
```

Critical design decisions:

1. Use `officeId + date` as the primary availability lookup.
2. Cache daily restaurants and menus for read scalability.
3. Validate all order placement rules transactionally.
4. Use idempotency keys to prevent duplicate orders.
5. Use transactional outbox for reliable async processing.
6. Batch orders by office, restaurant, and delivery date.
7. Scale large deployments by region and office.
8. Keep Redis as a cache, not source of truth.

---

## 25. Senior-Level Closing Statement

A strong interview summary:

> I would design LunchDrop as a scheduled corporate lunch ordering platform, not a generic instant food delivery marketplace. The main read path is office/date-based restaurant and menu browsing, so I would cache daily restaurant lists and versioned menus heavily. The main correctness path is order placement, where I would use PostgreSQL transactions to validate user-office authorization, restaurant schedule, cutoff time, menu item availability, customization rules, and price calculation. I would add idempotency keys to prevent duplicate orders and use a transactional outbox so notifications, restaurant dispatch, billing, and analytics are processed asynchronously without blocking the user. At small scale, I would start with a modular monolith and PostgreSQL. At national scale, I would split order, menu, schedule, dispatch, notification, and analytics services, partition data by region/office, and use caching plus event-driven processing to handle lunch-time traffic spikes.
