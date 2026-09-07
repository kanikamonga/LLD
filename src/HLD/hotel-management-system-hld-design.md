# High-Level Design: Hotel Management and Reservation System

## Original Problem Statement

Design a comprehensive hotel management system similar to what companies like Marriott, Hilton, or Expedia would use. This system needs to handle the complete lifecycle of hotel operations from room inventory management to guest reservations and check-in/check-out processes.

The system should support multiple hotels within a chain, handle real-time room availability, process reservations, manage pricing, and provide analytics for hotel operators.

### Functional Requirements

- Property onboarding: hotels, room types, amenities, rate plans, taxes, and fees.
- Inventory and availability management: by property, room type, and date range; allotments and blocks.
- Reservation lifecycle: search -> quote -> hold -> book -> pay -> modify/cancel -> refund.

---

## 1. Design Principle

The most important design principle is:

> Search availability can be eventually consistent, but hold and booking must use strong consistency for `(propertyId, roomTypeId, stayDate)` inventory to prevent overselling.

Hotel inventory is date-range based. A reservation from `2026-10-10` to `2026-10-13` consumes inventory for:

```text
2026-10-10
2026-10-11
2026-10-12
```

The checkout date is normally not consumed.

Inventory key:

```text
propertyId + roomTypeId + stayDate
```

Example:

```text
hotel_123 + deluxe_king + 2026-10-10
hotel_123 + deluxe_king + 2026-10-11
hotel_123 + deluxe_king + 2026-10-12
```

Search can use cached or indexed availability, but the hold/booking path must atomically reserve inventory across all stay dates.

---

## 2. Scope

### In scope

#### Hotel operator/admin flows

- Onboard hotel properties.
- Configure room types.
- Configure amenities.
- Configure rate plans.
- Configure taxes and fees.
- Manage room inventory by date.
- Block rooms for maintenance or group bookings.
- Manage pricing and promotions.
- View occupancy, revenue, cancellation, and demand analytics.

#### Guest/customer flows

- Search hotels by location and date range.
- View room availability and prices.
- Get a quote.
- Hold inventory temporarily.
- Book reservation.
- Pay.
- Modify or cancel booking.
- Check in and check out.

#### Platform capabilities

- Real-time or near-real-time availability.
- Strong inventory consistency during hold and booking.
- High read scalability for search.
- Reliable payment and refund workflows.
- Analytics pipeline.
- Monitoring and alerting.

### Out of scope for v1

- Loyalty points.
- Dynamic flight + hotel packaging.
- Corporate travel policy engine.
- Housekeeping staff mobile app.
- Complex overbooking optimization.
- AI-based price optimization.
- Multi-currency treasury settlement.
- Full third-party channel-manager synchronization.

---

## 3. Requirements

### Functional requirements

1. **Property onboarding**
   - Create hotel/property.
   - Add rooms and room types.
   - Add amenities.
   - Configure rate plans.
   - Configure taxes, fees, and cancellation policies.

2. **Inventory management**
   - Track availability by property, room type, and date.
   - Support room blocks.
   - Support allotments.
   - Support maintenance holds.
   - Support group booking inventory.

3. **Search**
   - Search properties by city/location/date range.
   - Filter by price, rating, amenities, room type, cancellation policy.
   - Return available rooms and price estimates.

4. **Quote**
   - Generate final price for selected room/date/rate plan.
   - Include taxes, fees, discounts, and cancellation policy.
   - Quote has expiry.

5. **Hold**
   - Temporarily reserve inventory before payment.
   - Prevent overselling.
   - Expire automatically.

6. **Booking**
   - Convert hold into confirmed reservation.
   - Store guest details.
   - Process payment.
   - Generate confirmation.

7. **Modify/cancel**
   - Modify dates, room type, guest count, or rate plan.
   - Cancel reservation.
   - Apply cancellation rules.
   - Trigger refund if applicable.

8. **Check-in/check-out**
   - Assign physical room.
   - Mark guest checked in.
   - Capture incidental deposit if needed.
   - Mark checked out.
   - Finalize charges.

9. **Analytics**
   - Occupancy rate.
   - ADR: average daily rate.
   - RevPAR: revenue per available room.
   - Booking conversion.
   - Cancellation rate.
   - Demand by location/date.
   - Inventory utilization.

### Non-functional requirements

| Requirement | Target |
|---|---:|
| Search latency | P95 < 300 ms |
| Booking latency | P95 < 1-2 seconds excluding payment |
| Availability | 99.99% for search and booking |
| Inventory correctness | No overselling for confirmed bookings |
| Search consistency | Eventual consistency acceptable |
| Booking consistency | Strong consistency required |
| Analytics freshness | Minutes to hours acceptable |
| Scale | Thousands of search QPS, hundreds of booking QPS |
| Durability | Reservation/payment data must not be lost |
| Horizontal scaling | API, search, pricing, inventory readers, workers |

---

## 4. Capacity Estimate

Assume a large hotel platform:

```text
Properties: 50,000
Room types per property: 5-20
Average room types per property: 10
Inventory horizon: 365 days
Inventory rows = 50,000 * 10 * 365 = 182.5 million rows
```

Traffic:

```text
Average search QPS: 5,000
Peak search QPS: 20,000
Booking conversion: 1-3%
Peak booking attempts: 200-600/sec
Confirmed bookings: lower than attempts
```

Design implications:

- Search must use cache/search index.
- Booking cannot depend only on search index.
- Inventory writes need careful concurrency control.
- Analytics should be offloaded from OLTP databases.

---

## 5. API Design

## 5.1 Search Hotels

```http
GET /v1/hotels/search
```

Example:

```text
/v1/hotels/search
  ?city=London
  &checkIn=2026-10-10
  &checkOut=2026-10-13
  &guests=2
  &rooms=1
  &currency=USD
  &amenities=pool,wifi
  &sort=best
  &pageToken=...
```

Response:

```json
{
  "requestId": "req_123",
  "results": [
    {
      "propertyId": "hotel_123",
      "name": "Grand London Hotel",
      "location": "London",
      "rating": 4.6,
      "availableRoomTypes": [
        {
          "roomTypeId": "deluxe_king",
          "roomTypeName": "Deluxe King",
          "ratePlanId": "refundable_breakfast",
          "price": 240,
          "currency": "USD",
          "taxesAndFees": 40,
          "totalPrice": 760,
          "availabilityStatus": "AVAILABLE",
          "freshnessTimestamp": "2026-09-23T17:00:00Z",
          "requiresRevalidation": true
        }
      ]
    }
  ],
  "nextPageToken": "opaque-token"
}
```

Notes:

- Search response may be stale.
- Include freshness metadata.
- Booking must call quote/hold before confirmation.

## 5.2 Get Quote

```http
POST /v1/quotes
```

Request:

```json
{
  "propertyId": "hotel_123",
  "roomTypeId": "deluxe_king",
  "ratePlanId": "refundable_breakfast",
  "checkIn": "2026-10-10",
  "checkOut": "2026-10-13",
  "guests": 2,
  "rooms": 1,
  "currency": "USD"
}
```

Response:

```json
{
  "quoteId": "quote_123",
  "propertyId": "hotel_123",
  "roomTypeId": "deluxe_king",
  "ratePlanId": "refundable_breakfast",
  "baseAmount": 720,
  "taxes": 60,
  "fees": 20,
  "totalAmount": 800,
  "currency": "USD",
  "cancellationPolicy": "Free cancellation until 24h before check-in",
  "expiresAt": "2026-09-23T17:10:00Z"
}
```

Quote should re-check price rules and approximate availability, but final inventory protection comes from hold.

## 5.3 Create Hold

```http
POST /v1/holds
Idempotency-Key: idem_123
```

Request:

```json
{
  "quoteId": "quote_123",
  "propertyId": "hotel_123",
  "roomTypeId": "deluxe_king",
  "checkIn": "2026-10-10",
  "checkOut": "2026-10-13",
  "rooms": 1
}
```

Response:

```json
{
  "holdId": "hold_123",
  "status": "HELD",
  "expiresAt": "2026-09-23T17:15:00Z"
}
```

Hold must atomically decrement available inventory or reserve units for every date in the stay range.

## 5.4 Create Reservation

```http
POST /v1/reservations
Idempotency-Key: idem_456
```

Request:

```json
{
  "holdId": "hold_123",
  "guest": {
    "firstName": "Asha",
    "lastName": "Sharma",
    "email": "asha@example.com",
    "phone": "+911234567890"
  },
  "paymentMethodId": "pm_123"
}
```

Response:

```json
{
  "reservationId": "res_123",
  "status": "CONFIRMED",
  "confirmationCode": "ABC123",
  "amount": 800,
  "currency": "USD"
}
```

## 5.5 Other APIs

```http
GET /v1/reservations/{reservationId}
POST /v1/reservations/{reservationId}/cancel
POST /v1/reservations/{reservationId}/check-in
POST /v1/reservations/{reservationId}/check-out
```

Cancellation and payment-related APIs should require idempotency keys.

---

## 6. Data Model

## 6.1 Property Data

Use PostgreSQL or another relational database.

```text
properties(
  property_id PK,
  chain_id,
  name,
  address,
  city,
  country,
  latitude,
  longitude,
  timezone,
  status,
  created_at,
  updated_at
)
```

```text
room_types(
  room_type_id PK,
  property_id FK,
  name,
  capacity,
  bed_type,
  base_occupancy,
  max_occupancy,
  total_rooms,
  status
)
```

```text
rooms(
  room_id PK,
  property_id FK,
  room_type_id FK,
  room_number,
  floor,
  status
)
```

```text
amenities(
  amenity_id PK,
  name
)
```

```text
property_amenities(
  property_id,
  amenity_id
)
```

Relational storage is appropriate because property configuration is structured and strongly related.

## 6.2 Rate Plans and Pricing

```text
rate_plans(
  rate_plan_id PK,
  property_id FK,
  room_type_id FK,
  name,
  cancellation_policy_id,
  meal_plan,
  refundable,
  status
)
```

```text
daily_rates(
  property_id,
  room_type_id,
  rate_plan_id,
  stay_date,
  base_price,
  currency,
  min_stay,
  max_stay,
  closed_to_arrival,
  closed_to_departure,
  version,
  PRIMARY KEY(property_id, room_type_id, rate_plan_id, stay_date)
)
```

```text
taxes_fees(
  tax_fee_id PK,
  property_id,
  type,
  calculation_type,
  amount_or_percentage,
  effective_from,
  effective_to
)
```

Pricing is date-based because hotel rates vary by day.

## 6.3 Inventory

Critical table:

```text
room_inventory(
  property_id,
  room_type_id,
  stay_date,
  total_rooms,
  blocked_rooms,
  held_rooms,
  booked_rooms,
  available_rooms,
  version,
  updated_at,
  PRIMARY KEY(property_id, room_type_id, stay_date)
)
```

Where:

```text
available_rooms = total_rooms - blocked_rooms - held_rooms - booked_rooms
```

In practice, `available_rooms` can be materialized for fast conditional updates.

## 6.4 Holds

```text
inventory_holds(
  hold_id PK,
  property_id,
  room_type_id,
  check_in,
  check_out,
  rooms,
  status,
  expires_at,
  idempotency_key,
  created_at,
  updated_at
)
```

Hold statuses:

```text
HELD
EXPIRED
CONFIRMED
RELEASED
```

## 6.5 Reservations

```text
reservations(
  reservation_id PK,
  property_id,
  room_type_id,
  rate_plan_id,
  hold_id,
  user_id,
  guest_id,
  check_in,
  check_out,
  rooms,
  status,
  total_amount,
  currency,
  payment_id,
  confirmation_code,
  created_at,
  updated_at
)
```

Reservation statuses:

```text
PENDING_PAYMENT
CONFIRMED
MODIFICATION_PENDING
CANCELLED
CHECKED_IN
CHECKED_OUT
NO_SHOW
REFUND_PENDING
REFUNDED
FAILED
```

## 6.6 Payments

```text
payments(
  payment_id PK,
  reservation_id,
  amount,
  currency,
  status,
  provider,
  provider_payment_ref,
  created_at,
  updated_at
)
```

Payment statuses:

```text
AUTHORIZED
CAPTURED
FAILED
VOIDED
REFUND_PENDING
REFUNDED
```

---

## 7. High-Level Architecture

```text
Clients / Web / Mobile / Partner APIs
        |
        v
CDN / Edge Cache
        |
        v
API Gateway + Auth + Rate Limiting
        |
        +-----------------------+-------------------------+
        |                       |                         |
        v                       v                         v
 Search Service          Reservation Service       Property Admin Service
        |                       |                         |
        v                       v                         v
 Redis Cache             Inventory Service         Property DB
        |                       |
        v                       v
 Search Index            PostgreSQL / Inventory DB
        |                       |
        v                       v
 Pricing Service         Payment Service
                                |
                                v
                         Payment Gateway
```

Async pipeline:

```text
Property / Rate / Inventory / Reservation Changes
        |
        v
Kafka / Event Bus
        |
        +------------------------+-------------------------+
        |                        |                         |
        v                        v                         v
Search Index Updater     Analytics Pipeline        Notification Worker
        |                        |                         |
        v                        v                         v
OpenSearch               Data Lake / Warehouse      Email / SMS / Push
```

---

## 8. Main Services

| Service | Responsibilities |
|---|---|
| API Gateway | Authentication, authorization, rate limiting, request validation, tenant isolation |
| Search Service | Query normalization, Redis lookup, OpenSearch query, ranking, freshness metadata |
| Pricing Service | Date-range price calculation, rate plan application, promotions, taxes, quote generation |
| Inventory Service | Holds, releases, blocks, cancellations, inventory consistency, oversell prevention |
| Reservation Service | Reservation lifecycle, idempotency, saga orchestration, state transitions |
| Payment Service | Authorize, capture, void, refund, payment webhook handling, reconciliation |
| Property Admin Service | Property onboarding, room types, amenities, taxes, rate plans, inventory blocks |
| Analytics Service | Occupancy, revenue, demand, cancellation, conversion, inventory utilization |

---

## 9. Search Flow

```text
User searches city/date/guests
  -> API Gateway
  -> Search Service
  -> Normalize query
  -> Redis cache lookup
       -> cache hit:
            return cached properties/room types with freshness metadata
       -> cache miss:
            query OpenSearch
            optionally enrich with pricing summary
            cache result
            return response
```

Search index stores denormalized documents:

```json
{
  "propertyId": "hotel_123",
  "city": "London",
  "location": {
    "lat": 51.5072,
    "lon": -0.1276
  },
  "amenities": ["wifi", "pool", "gym"],
  "rating": 4.6,
  "roomTypes": [
    {
      "roomTypeId": "deluxe_king",
      "capacity": 2,
      "availableDateRanges": ["2026-10-10:2026-10-13"],
      "minPrice": 240,
      "currency": "USD"
    }
  ],
  "freshnessTimestamp": "2026-09-23T17:00:00Z"
}
```

Search result can be stale, so response includes:

```json
{
  "requiresRevalidation": true
}
```

---

## 10. Quote Flow

```text
User selects room/rate plan
  -> Pricing Service
  -> Fetch latest daily rates
  -> Fetch taxes and fees
  -> Check approximate inventory
  -> Generate quote with expiry
```

Example:

```text
room = deluxe_king
dates = Oct 10, Oct 11, Oct 12
daily rates = 200 + 240 + 280
base = 720
tax = 60
fee = 20
total = 800
```

Quote does not guarantee inventory unless combined with hold.

---

## 11. Hold Flow

This is where strong consistency is required.

For:

```text
checkIn = 2026-10-10
checkOut = 2026-10-13
rooms = 1
```

Need to reserve:

```text
2026-10-10
2026-10-11
2026-10-12
```

### SQL transaction with conditional updates

Inside one DB transaction:

```sql
UPDATE room_inventory
SET held_rooms = held_rooms + 1,
    available_rooms = available_rooms - 1,
    version = version + 1
WHERE property_id = ?
  AND room_type_id = ?
  AND stay_date IN (?, ?, ?)
  AND available_rooms >= 1;
```

Then check:

```text
affected rows must equal number of stay dates
```

If yes:

```text
create inventory_holds row
commit
```

If no:

```text
rollback
return SOLD_OUT
```

Recommended approach:

> Use relational DB transactions partitioned/sharded by `propertyId`, with conditional updates on `available_rooms`.

---

## 12. Booking Flow

```text
User clicks book
  -> Create quote
  -> Create inventory hold
  -> Authorize payment
  -> Confirm reservation
  -> Capture payment
  -> Convert hold to booked inventory
  -> Send confirmation
```

Detailed flow:

```text
1. Client sends POST /reservations with Idempotency-Key.
2. Reservation Service validates quote.
3. Inventory Service creates hold.
4. Reservation created as PENDING_PAYMENT.
5. Payment Service authorizes payment.
6. If payment succeeds:
     - Convert hold to confirmed booking.
     - Decrement held_rooms.
     - Increment booked_rooms.
     - Mark reservation CONFIRMED.
     - Capture payment.
7. If payment fails:
     - Release hold.
     - Mark reservation FAILED.
8. Send confirmation email/SMS.
```

Payment and inventory updates are a distributed workflow. Use the saga pattern.

---

## 13. Booking State Machine

```text
INITIATED
QUOTE_CREATED
HOLD_CREATED
PENDING_PAYMENT
PAYMENT_AUTHORIZED
CONFIRMED
PAYMENT_FAILED
HOLD_EXPIRED
CANCELLED
MODIFICATION_PENDING
CHECKED_IN
CHECKED_OUT
NO_SHOW
REFUND_PENDING
REFUNDED
FAILED
```

Do not model every retry as a separate state. Keep retries as metadata.

---

## 14. Saga and Failure Handling

### Payment succeeds but reservation confirmation fails

Possible causes:

- DB timeout.
- Inventory conversion failed.
- Service crash.

Handling:

1. Keep reservation in `PAYMENT_AUTHORIZED` or `CONFIRMATION_PENDING`.
2. Retry confirmation idempotently.
3. If hold is still valid, convert hold to booking.
4. If hold expired and inventory unavailable, void/refund payment.
5. Reconcile stuck records using background worker.

### Inventory hold succeeds but payment fails

Handling:

1. Mark reservation `PAYMENT_FAILED`.
2. Release hold.
3. Increment `available_rooms`.
4. Decrement `held_rooms`.
5. Notify user.

### Client retries booking request

Use idempotency key:

```text
same idempotency key + same request body -> return original result
same idempotency key + different body -> reject
```

### Hold expires

Background worker scans expired holds:

```text
status = HELD
expires_at < now()
```

For each expired hold:

```text
decrement held_rooms
increment available_rooms
mark hold EXPIRED
emit InventoryReleased event
```

This must be idempotent.

---

## 15. Modification Flow

Modification is tricky because it may require changing inventory.

Example: user changes stay from:

```text
Oct 10-13
```

to:

```text
Oct 10-15
```

Need additional dates:

```text
Oct 13
Oct 14
```

Safe flow:

```text
1. Create quote for new itinerary.
2. Hold additional inventory or new inventory.
3. Compute price difference.
4. Authorize/capture additional payment or calculate refund.
5. Commit modification.
6. Release old inventory if needed.
```

Do not release old booking inventory until the new inventory is secured.

---

## 16. Cancellation Flow

```text
User cancels reservation
  -> Reservation Service checks policy
  -> Calculate refund
  -> Update reservation CANCELLED
  -> Release booked inventory
  -> Trigger refund if applicable
  -> Emit cancellation event
```

Inventory release:

```text
booked_rooms -= rooms
available_rooms += rooms
```

For each consumed date.

Refund may be async:

```text
CANCELLED -> REFUND_PENDING -> REFUNDED
```

---

## 17. Check-in / Check-out Flow

### Check-in

```text
Guest arrives
  -> Validate reservation CONFIRMED
  -> Verify identity
  -> Assign physical room
  -> Capture incidental deposit if required
  -> Mark CHECKED_IN
```

Room assignment:

```text
room_assignments(
  reservation_id,
  room_id,
  assigned_at,
  status
)
```

Physical room assignment happens close to check-in because hotels usually book a room type, not an exact room number.

### Check-out

```text
Guest checks out
  -> Add final charges
  -> Capture final payment if needed
  -> Mark CHECKED_OUT
  -> Mark physical room DIRTY
  -> Notify housekeeping
```

Room status:

```text
AVAILABLE
OCCUPIED
DIRTY
MAINTENANCE
OUT_OF_SERVICE
```

---

## 18. Event-Driven Architecture

Use Kafka/Event Bus.

Important events:

```text
PropertyCreated
RoomTypeCreated
RatePlanUpdated
InventoryChanged
QuoteCreated
HoldCreated
HoldExpired
ReservationCreated
ReservationConfirmed
ReservationCancelled
PaymentAuthorized
PaymentCaptured
RefundIssued
GuestCheckedIn
GuestCheckedOut
```

Consumers:

```text
Search Index Updater
Analytics Pipeline
Notification Service
Revenue Management Service
Audit Log Service
```

Use at-least-once delivery with idempotent consumers.

---

## 19. Partitioning and Sharding

### Inventory DB

Recommended shard key:

```text
propertyId
```

Reason:

- Booking affects one property.
- Date-range inventory rows for one property should be transactionally updated together.
- Reduces cross-shard transactions.

Potential issue:

- Very large properties or high-demand hotels can become hot.

Mitigation:

- Partition hot property inventory by room type.
- Use row-level locking.
- Use optimistic concurrency.
- Use per-property write queues for extreme hot spots.

### Kafka

Partition key:

```text
propertyId
```

or:

```text
propertyId + roomTypeId
```

Use `propertyId` when event ordering for a property is important. Use `propertyId + roomTypeId` for more parallelism.

### Search Index

Use geo/location indexing because search by city/location is common.

Possible routing dimensions:

```text
city/location
propertyId
```

### Reservations DB

Initially do not shard prematurely.

If needed:

```text
hash(reservationId)
```

Indexes:

```text
(user_id, created_at)
(property_id, check_in)
(confirmation_code)
(status, updated_at)
```

---

## 20. Caching Strategy

### Search cache

Cache key:

```text
search:{city}:{checkIn}:{checkOut}:{guests}:{rooms}:{filtersHash}:{sort}:{page}
```

TTL:

```text
30-120 seconds for popular searches
2-5 minutes for low-traffic searches
```

Search cache can be stale.

### Property metadata cache

Cache:

```text
property:{propertyId}
roomTypes:{propertyId}
amenities:{propertyId}
```

Metadata TTL can be longer because property data changes less often.

### Pricing cache

Cache computed date-range price carefully:

```text
price:{propertyId}:{roomTypeId}:{ratePlanId}:{checkIn}:{checkOut}:{guests}
```

TTL should be short because rates can change.

### Inventory cache

Do not use cache as source of truth for booking. Use cache only for search display.

---

## 21. Failure Scenarios

### Redis down

Impact:

- Search latency increases.
- More load goes to OpenSearch.

Handling:

- Fail fast on Redis calls.
- Use local cache for hot searches.
- Protect OpenSearch with rate/concurrency limits.
- Degrade filters/sorts if needed.

### OpenSearch down

Impact:

- Search is degraded.

Handling:

- Serve cached popular searches.
- Return temporary unavailable for long-tail searches.
- Do not affect existing bookings.
- Rebuild index from events after recovery.

### Inventory DB unavailable

Impact:

- Cannot create holds or bookings safely.

Handling:

- Search can remain available with stale data.
- Booking/hold should fail gracefully.
- Do not accept bookings that cannot reserve inventory.
- Alert immediately.

### Payment gateway timeout

Handling:

- Keep reservation in `PAYMENT_PENDING` or `PAYMENT_UNKNOWN`.
- Query payment provider by idempotency key/payment reference.
- Do not retry blindly if payment may have succeeded.
- Reconcile asynchronously.

### Service crashes after payment authorization

Handling:

- Persist state before external calls where possible.
- Use idempotency keys.
- Use reconciliation worker.
- Void/refund if reservation cannot be confirmed.

### Hold expiry worker fails

Handling:

- Holds have `expires_at`.
- Booking service must check hold validity before confirmation.
- Multiple workers can safely process expiry using idempotent updates.
- Delayed expiry reduces availability temporarily but should not oversell.

---

## 22. Consistency Model

| Operation | Consistency | Reason |
|---|---|---|
| Search hotel availability | Eventual | Low latency and high availability |
| Search price display | Eventual | Final quote recalculates |
| Quote | Stronger than search but expiring | Price must be recent and bounded by expiry |
| Hold inventory | Strong | Prevent overselling |
| Confirm reservation | Strong | Booking correctness |
| Payment | Strong/idempotent | Money movement |
| Cancel/refund | Strong workflow with async refund | Inventory and money must reconcile |
| Analytics | Eventual | Reports can tolerate delay |

---

## 23. Observability

### Search metrics

```text
search_qps
search_latency_p50_p95_p99
search_error_rate
cache_hit_ratio
opensearch_latency
stale_search_percentage
```

### Inventory metrics

```text
hold_success_rate
hold_failure_rate
sold_out_rate
inventory_update_latency
oversell_incidents
hold_expiry_lag
inventory_lock_contention
```

### Reservation metrics

```text
reservation_created_count
reservation_confirmed_count
reservation_failed_count
cancellation_rate
modification_rate
stuck_reservations_by_state
```

### Payment metrics

```text
payment_authorization_success_rate
payment_capture_success_rate
payment_timeout_rate
refund_success_rate
payment_reconciliation_lag
```

### Business metrics

```text
occupancy_rate
ADR
RevPAR
booking_conversion_rate
cancellation_rate
revenue_by_property
demand_by_city
```

### Alerts

Alert on:

- Search P95 above SLA.
- Booking failure spike.
- Inventory DB latency spike.
- Oversell detection.
- Payment gateway error spike.
- Holds not expiring.
- Kafka lag.
- Search index freshness lag.
- Stuck reservations.
- Refund failure spike.

---

## 24. Security

- Use TLS everywhere.
- Encrypt PII.
- Do not store raw card data.
- Use payment tokens.
- Use role-based access control for hotel operators.
- Audit admin changes to inventory/pricing.
- Rate-limit public APIs.
- Protect against bot scraping.
- Store secrets in managed secrets manager.
- Support GDPR/PII deletion where applicable.

---

## 25. Disaster Recovery

### Multi-AZ

Deploy across multiple availability zones:

```text
API Gateway
Search Service
Reservation Service
Inventory Service
Redis
Kafka
OpenSearch
PostgreSQL
```

### Multi-region

| Component | Strategy |
|---|---|
| Search | Active-active |
| Property metadata | Replicated |
| Analytics | Multi-region replication or delayed copy |
| Booking/inventory | Active-passive or region-owned |
| Payments | Idempotent and region-aware |

Booking active-active is hard because inventory consistency is critical.

For v1:

> Use active-active search, but active-passive booking/inventory failover.

### Recovery

- PostgreSQL PITR backups.
- Cross-region replicas.
- Kafka/event replication.
- Rebuild OpenSearch from events.
- Rebuild Redis from OpenSearch/database.
- Reconciliation jobs for payments/reservations.

---

## 26. Zero-Downtime Deployment

### Services

Use:

- Rolling deployment.
- Canary deployment.
- Health checks.
- Readiness probes.
- Backward-compatible APIs.
- Graceful shutdown.

Reservation Service must not drop in-flight bookings. Long-running workflows should resume from persisted state.

### Database migration

Use expand-contract pattern:

```text
1. Add nullable column/table.
2. Deploy code writing both old and new fields.
3. Backfill.
4. Switch reads.
5. Remove old field later.
```

### Search index migration

Use alias-based migration:

```text
hotel_search_v1
hotel_search_v2
hotel_search_current -> hotel_search_v2
```

Build v2 in parallel, validate, then atomically switch alias.

---

## 27. Key Trade-offs

| Decision | Reason | Trade-off |
|---|---|---|
| Search from OpenSearch | Fast filtering/ranking by city/date/amenities | Eventually consistent |
| Redis search cache | Absorbs repeated high-QPS searches | Stale data/invalidation complexity |
| PostgreSQL inventory with transactions | Prevent overselling | Scaling requires careful sharding |
| Hold before payment | Prevents payment without inventory | Holds temporarily reduce availability |
| Payment authorization before capture | Easier reversal if booking fails | Requires payment provider support |
| Kafka events | Decouples search/analytics/notifications | Operational complexity |
| Active-passive booking | Simpler inventory correctness | Slower regional failover |
| `propertyId` inventory shard | Keeps booking transaction local | Hot properties need mitigation |

---

## 28. Final Architecture Summary

```text
Guests / Operators / Partners
        |
        v
API Gateway + Auth + Rate Limiting
        |
        +-------------------+-------------------+-------------------+
        |                   |                   |
        v                   v                   v
 Search Service      Reservation Service   Property Admin Service
        |                   |                   |
        v                   v                   v
 Redis + OpenSearch  Inventory Service     Property DB
        |                   |
        v                   v
 Search Results      Inventory DB
                            |
                            v
                     Payment Service
                            |
                            v
                     Payment Gateway
```

Async side:

```text
Inventory / Reservation / Rate / Property Events
        |
        v
Kafka
        |
        +--------------------+---------------------+------------------+
        |                    |                     |
        v                    v                     v
Search Index Updater   Analytics Pipeline   Notification Service
        |                    |                     |
        v                    v                     v
OpenSearch             Data Warehouse        Email/SMS/Push
```

---

## 29. Interview-Level Closing Statement

A strong Senior-level summary:

> I would design search as an eventually consistent, highly cached read path backed by OpenSearch and Redis. However, I would not trust search availability for booking. For booking, I would use a strongly consistent Inventory Service that atomically creates holds across `(propertyId, roomTypeId, stayDate)` rows. Reservation confirmation is implemented as a saga across inventory, payment, and reservation state. PostgreSQL is a good fit for inventory and reservations because we need transactional updates and idempotency. Kafka is used to update search indexes, analytics, and notifications asynchronously. This gives low-latency search while preserving correctness in the booking path.
