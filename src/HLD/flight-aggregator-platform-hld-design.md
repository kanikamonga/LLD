# High-Level Design: Flight Aggregator Platform with Booking

## 1. Problem Statement

Design a flight aggregator platform that collects flight schedules, prices, and availability from multiple airlines, Global Distribution Systems (GDS), and travel agencies. The platform serves end users who want to search for flights and book flights inside the platform.

The system must:

- Provide a low-latency flight search API.
- Return the best flight options for origin, destination, and date range.
- Support real-time or near-real-time price and availability updates from providers.
- Handle thousands of search queries per second.
- Remain highly available even if individual providers fail.
- Store historical flight data for analytics.
- Support in-platform booking, payment coordination, and provider confirmation.
- Scale all major services horizontally.
- Provide monitoring, logging, tracing, and alerting.

The main design principle is:

> Search can be eventually consistent, but booking must revalidate price and availability with the provider before payment or ticket confirmation.

---

## 2. Scope

### In scope

- Flight search by origin, destination, departure date, return date, passenger count, and cabin class.
- Ranking and filtering flight options.
- Near-real-time ingestion from airlines, GDS systems, and travel agencies.
- Search result caching for high-volume routes.
- Freshness metadata on search results.
- Provider outage handling and degraded search behavior.
- In-platform booking.
- Offer revalidation before booking.
- Temporary provider hold or reservation.
- Payment authorization/capture coordination.
- Ticket confirmation with airline/GDS provider.
- Booking state management.
- Refund or void flow when payment succeeds but provider confirmation fails.
- Historical event storage and analytics.
- Observability and disaster recovery.

### Out of scope for initial version

- Multi-city itinerary search.
- Loyalty points and rewards.
- Complex post-booking modifications.
- Full refund rules engine.
- Corporate travel policy engine.
- Dynamic packaging with hotels/cabs.
- User profile management beyond what booking requires.

---

## 3. Requirements

### Functional requirements

1. Users can search flights by:
   - Origin.
   - Destination.
   - Departure date.
   - Optional return date.
   - Passenger count.
   - Cabin class.
   - Filters such as airline, number of stops, duration, and price range.
2. Search API returns ranked flight options.
3. Search results include price, availability, provider, freshness timestamp, and expiry metadata.
4. The platform ingests price/schedule/availability updates from external providers.
5. The platform stores historical flight data for analytics.
6. Users can select a flight offer and start booking.
7. The system revalidates price and availability with the provider before booking.
8. The system can create a temporary provider hold where supported.
9. The system coordinates payment and provider confirmation.
10. Users can query booking status.
11. The platform handles duplicate booking requests safely.
12. The platform provides degraded behavior when providers or internal components fail.

### Non-functional requirements

| Requirement | Target or assumption |
|---|---:|
| Average search QPS | 5,000 |
| Peak search QPS | 15,000 |
| Search latency | P95 < 300 ms, P99 < 800 ms |
| Provider update volume | 2,000-5,000 events/sec |
| Providers | 100+ airlines/GDS/travel agencies |
| Availability | 99.99% for search and booking |
| Read-to-booking ratio | Approximately 500:1 |
| Search consistency | Eventual consistency |
| Booking consistency | Strong state transitions within our system |
| Analytics consistency | Eventual consistency |
| Scaling | Horizontal scaling for stateless services, consumers, caches, and indexes |

### Capacity estimate

Assume:

```text
Average search QPS = 5,000
Peak search QPS = 15,000
Booking conversion from search = 1%
```

Then:

```text
Average booking attempts/sec = 5,000 * 1% = 50/sec
Peak booking attempts/sec = 15,000 * 1% = 150/sec
```

Confirmed bookings will be lower than booking attempts because users may abandon payment, offers may expire, payment can fail, and provider confirmation can fail.

This split matters:

- Search traffic is very high and read-heavy.
- Provider update traffic is high and write-heavy.
- Booking traffic is much lower but requires correctness, idempotency, and strong state transitions.

---

## 4. API Design

## 4.1 Search Flights

```http
GET /v1/flights/search
```

Example:

```text
/v1/flights/search
  ?origin=DEL
  &destination=LHR
  &departureDate=2026-10-10
  &returnDate=2026-10-20
  &passengers=2
  &cabin=ECONOMY
  &sort=best
  &pageToken=opaque-token
```

### Response

```json
{
  "requestId": "req_123",
  "results": [
    {
      "offerId": "off_123",
      "routeKey": "DEL:LHR:2026-10-10",
      "origin": "DEL",
      "destination": "LHR",
      "departureTime": "2026-10-10T02:30:00Z",
      "arrivalTime": "2026-10-10T10:20:00Z",
      "airline": "AI",
      "providerId": "amadeus",
      "price": 720,
      "currency": "USD",
      "seatsAvailable": 4,
      "stops": 1,
      "durationMinutes": 710,
      "freshnessTimestamp": "2026-09-23T13:10:00Z",
      "expiresAt": "2026-09-23T13:15:00Z",
      "freshnessStatus": "FRESH",
      "requiresRevalidation": true
    }
  ],
  "nextPageToken": "opaque-token",
  "partialResults": false
}
```

### Notes

- Search is served from internal cache/search index, not by synchronously calling airlines.
- Search result price can be stale.
- Every offer must be revalidated before booking.
- Pagination should use opaque cursors rather than page numbers.

---

## 4.2 Revalidate Offer

```http
POST /v1/flights/offers/{offerId}/validate
```

### Request

```json
{
  "passengers": 2,
  "cabin": "ECONOMY"
}
```

### Response

```json
{
  "validatedOfferId": "vo_123",
  "offerId": "off_123",
  "status": "VALID",
  "latestPrice": 720,
  "currency": "USD",
  "providerId": "amadeus",
  "expiresAt": "2026-09-23T13:20:00Z"
}
```

### Error cases

| Error | Meaning |
|---|---|
| `OFFER_EXPIRED` | Provider no longer has this offer |
| `PRICE_CHANGED` | Price changed and user must accept latest price |
| `NO_AVAILABILITY` | Seats are no longer available |
| `PROVIDER_TIMEOUT` | Provider could not be reached within timeout |

`POST` is preferred over `GET` because validation may call an external provider and can create a temporary provider-side quote or hold depending on provider behavior.

---

## 4.3 Create Booking

```http
POST /v1/bookings
Idempotency-Key: idem_abc123
```

### Request

```json
{
  "validatedOfferId": "vo_123",
  "passengers": [
    {
      "firstName": "Asha",
      "lastName": "Sharma",
      "dob": "1992-04-10",
      "passportNumber": "P1234567"
    }
  ],
  "paymentMethodId": "pm_123"
}
```

### Response

```json
{
  "bookingId": "bk_123",
  "status": "CONFIRMATION_PENDING",
  "amount": 720,
  "currency": "USD"
}
```

### Notes

- `Idempotency-Key` is mandatory.
- Duplicate client retries must return the same booking result.
- Booking service also needs idempotency when calling payment provider and airline/GDS where supported.

---

## 4.4 Get Booking Status

```http
GET /v1/bookings/{bookingId}
```

### Response

```json
{
  "bookingId": "bk_123",
  "status": "CONFIRMED",
  "providerBookingRef": "PNR123",
  "paymentStatus": "CAPTURED",
  "createdAt": "2026-09-23T13:20:00Z",
  "updatedAt": "2026-09-23T13:22:00Z"
}
```

---

## 5. Data Model and Storage

## 5.1 Search Index: OpenSearch/Elasticsearch

Use OpenSearch/Elasticsearch as a derived read model for flight search.

### Why

Flight search requires:

- Filtering by origin, destination, date, cabin, stops, airline, and price.
- Sorting by price, duration, departure time, and ranking score.
- Faceting and pagination.
- Low-latency reads at high QPS.

### Document shape

```json
{
  "offerId": "off_123",
  "routeKey": "DEL:LHR:2026-10-10",
  "origin": "DEL",
  "destination": "LHR",
  "departureDate": "2026-10-10",
  "departureTime": "2026-10-10T02:30:00Z",
  "arrivalTime": "2026-10-10T10:20:00Z",
  "airline": "AI",
  "providerId": "amadeus",
  "price": 720,
  "currency": "USD",
  "seatsAvailable": 4,
  "stops": 1,
  "durationMinutes": 710,
  "freshnessTimestamp": "2026-09-23T13:10:00Z",
  "expiresAt": "2026-09-23T13:15:00Z",
  "version": 42
}
```

### Indexing strategy

Important indexed fields:

- `origin`
- `destination`
- `departureDate`
- `returnDate`
- `cabin`
- `airline`
- `providerId`
- `price`
- `durationMinutes`
- `stops`
- `freshnessTimestamp`

Use `routeKey = origin:destination:departureDate` as a routing key where appropriate to reduce shard fanout for common route/date queries.

OpenSearch is not the source of truth. It can be rebuilt from Kafka/object storage.

---

## 5.2 Historical Data: Object Storage + Warehouse

Use:

```text
Kafka/Event Bus -> Raw object storage -> Snowflake/Data warehouse
```

### Why

Historical analytics need:

- Cheap long-term storage.
- Replayability.
- Large analytical scans.
- Trend analysis.
- Provider quality analysis.
- Price movement analysis.

Snowflake or a similar warehouse is good for analytics, but not for real-time serving.

---

## 5.3 Bookings: PostgreSQL

Use PostgreSQL for booking records because booking requires ACID state transitions.

### Tables

```text
bookings(
  booking_id PK,
  user_id,
  validated_offer_id,
  provider_id,
  provider_hold_id,
  provider_booking_ref,
  status,
  total_amount,
  currency,
  payment_id,
  hold_expires_at,
  created_at,
  updated_at
)
```

```text
booking_passengers(
  passenger_id PK,
  booking_id FK,
  first_name,
  last_name,
  dob,
  passport_number
)
```

```text
idempotency_keys(
  idempotency_key PK,
  user_id,
  request_hash,
  booking_id,
  status,
  expires_at
)
```

### Indexes

```text
PK booking_id
INDEX (user_id, created_at)
UNIQUE (provider_id, provider_booking_ref)
UNIQUE (idempotency_key)
INDEX (status, updated_at)
```

### Sharding note

Do not shard PostgreSQL bookings prematurely. Booking QPS is much lower than search QPS. Start with:

- Multi-AZ primary.
- Standby replica.
- Read replicas.
- Connection pooling.
- Table partitioning by time if needed.

If sharding becomes necessary later, prefer hash-based sharding on `booking_id` to distribute writes evenly. Keep secondary indexes/read models for user booking history and provider lookup.

---

## 5.4 Provider Metadata: DynamoDB or PostgreSQL

Provider metadata can be stored in DynamoDB or PostgreSQL depending on consistency and query requirements.

Example fields:

```text
provider_id
provider_type
api_endpoint
auth_config_reference
rate_limit
timeout_policy
circuit_breaker_state
last_successful_update
enabled
```

Use `provider_id` as the primary key. Avoid storing credentials directly in the database; store references to secrets in a managed secrets manager.

---

## 6. High-Level Architecture

![Flight Aggregator High-Level Architecture](../../docs/flight-aggregator-high-level-architecture.png)

PNG source: `../../docs/flight-aggregator-high-level-architecture.png`

```text
Clients / Web / Mobile
        |
        v
CDN / Edge Cache
        |
        v
API Gateway + Auth + Rate Limiting
        |
        +--------------------------+
        |                          |
        v                          v
 Search Service              Booking Service
        |                          |
        v                          v
 Redis Cache                PostgreSQL Bookings DB
        |                          |
        v                          v
OpenSearch/Elastic          Payment Gateway
        |                          |
        v                          v
 Ranking Service            Airline/GDS APIs
        |
        v
Search Response with freshness metadata
```

Ingestion:

```text
Airline/GDS APIs + Webhooks
        |
        v
Provider Connectors
        |
        v
Ingestion Service
        |
        v
Kafka / Event Bus
        |
        v
Normalizer + Deduplicator + Validator
        |
        +--------------------------+
        |                          |
        v                          v
Search Index Updater         Raw Event Storage
        |                          |
        v                          v
OpenSearch                 Snowflake/Data Warehouse
```

---

## 7. Search Read Path

```text
Client
 -> CDN/API Gateway
 -> Search Service
 -> Redis cache lookup
      -> hit: return cached result with freshness metadata
      -> miss: query OpenSearch
 -> Ranking/filtering
 -> Cache response
 -> Return response
```

The normal search path must not synchronously fan out to airline APIs. Doing that would make P95 < 300 ms unrealistic and would expose users to provider latency and outages.

### Cache key

Use normalized query fields:

```text
search:{origin}:{destination}:{departureDate}:{returnDate}:{passengers}:{cabin}:{filtersHash}:{sort}:{page}
```

Example:

```text
search:DEL:LHR:2026-10-10:2026-10-20:2:ECONOMY:nonstop=false:sort=best:page=1
```

To improve hit rate, cache common base route/date queries separately:

```text
route-date:{origin}:{destination}:{departureDate}:{returnDate}:{cabin}
```

Then apply some filtering/ranking in the Search Service.

### TTL

| Data | Example TTL |
|---|---:|
| Popular route/date search results | 30-120 seconds |
| Low-traffic route search results | 2-5 minutes |
| Provider unavailable fallback | 5-15 minutes, marked stale |
| Booking validation | No cache-only decision |

### Cache bypass

Bypass cache when:

- User explicitly requests latest price.
- Cache entry is older than freshness SLA.
- Route/date is close to departure and price volatility is high.
- User is in booking/revalidation flow.
- Provider-specific freshness is required.

### Hot route protection

For hot routes such as `DEL-BOM` or `NYC-LON`:

1. Use request coalescing/single-flight so one cache miss does not create thousands of OpenSearch queries.
2. Serve stale while asynchronously refreshing.
3. Proactively warm popular route/date caches.
4. Use Redis clustering, sharding, and replicas.
5. Use OpenSearch routing by `routeKey`.
6. Apply per-user, per-IP, per-client, and per-route rate limits.
7. Return cached top-N degraded results if OpenSearch is slow.

---

## 8. Ingestion and Freshness

```text
Provider Updates
 -> Ingestion Service
 -> Kafka
 -> Normalizer/Deduplicator
 -> Search Index Updater
 -> OpenSearch
 -> Redis invalidation/refresh
 -> Raw object storage
 -> Analytics warehouse
```

### Kafka topic strategy

Possible strategy:

```text
topic: provider-updates
partition key: providerId + routeKey
```

This provides provider isolation and enough parallelism across routes.

If exact ordering for a single offer is critical:

```text
partition key = offerId
```

Trade-off:

- `providerId` alone can create hot partitions for large providers.
- `providerId + routeKey` spreads load better.
- `offerId` improves per-offer ordering but may make route-level aggregation harder.

### Handling index lag

If OpenSearch indexing lags by 2 minutes:

- Keep search available.
- Include `freshnessTimestamp`, `expiresAt`, and `freshnessStatus`.
- Mark stale providers/routes.
- Deprioritize or suppress stale offers beyond a safety threshold.
- Always revalidate before booking.
- Alert on freshness SLA violation.
- Autoscale consumers.
- Use bulk OpenSearch indexing.
- Coalesce superseded updates for the same `offerId`.

### Lagging provider

If one provider lags by 30 minutes:

1. Mark that provider's offers as stale.
2. Continue showing healthy providers.
3. Scale consumers up to available partitions.
4. Use bulk indexing and batch tuning.
5. Coalesce obsolete updates.
6. Throttle or isolate noisy provider traffic.
7. Reconcile from provider snapshots after lag drains.

The failure of one provider must not degrade the entire platform.

---

## 9. Booking Flow

Booking is the most consistency-sensitive part of the system.

### Normal booking flow

```text
User selects search result
 -> Booking Service creates booking_id with INITIATED
 -> Revalidate offer with provider
 -> Create temporary provider hold
 -> Authorize payment
 -> Confirm/ticket with provider
 -> Capture payment
 -> Store provider booking reference
 -> Mark booking CONFIRMED
 -> Notify user
```

### Recommended state machine

```text
INITIATED
VALIDATING_OFFER
HOLD_CREATED
PAYMENT_AUTHORIZED
CONFIRMING_WITH_PROVIDER
CONFIRMED
PAYMENT_FAILED
PROVIDER_FAILED
CANCELLED
REFUND_PENDING
REFUNDED
CONFIRMATION_PENDING
UNKNOWN_PROVIDER_STATUS
```

Avoid modeling every retry as a user-visible state. Retries should usually be metadata around a stable lifecycle state.

### Why revalidation is mandatory

Search results can be stale. Before booking:

- Call provider/GDS for latest price.
- Confirm availability.
- Detect price change.
- Detect expired offers.
- Create a short-lived validated offer or hold.

SearchDB/OpenSearch timestamp is useful for warnings, but it is not authoritative for final booking.

---

## 10. Booking Failure Handling

### Payment succeeds but provider booking fails

If payment authorization/capture succeeds and provider confirmation fails:

1. Retry provider confirmation if safe.
2. If confirmation is not possible, void authorization or refund captured payment.
3. Mark booking `PROVIDER_FAILED` or `REFUND_PENDING`.
4. Notify user with clear status.
5. Emit event for reconciliation and operations review.

### Provider hold succeeds but payment fails

If provider hold succeeds and payment fails:

1. Let user retry payment within hold expiry window.
2. If retry succeeds, continue confirmation.
3. If retry does not happen before expiry, cancel/release provider hold.
4. Mark booking `PAYMENT_FAILED` or `CANCELLED`.

### Provider confirmation call times out

This is an ambiguous state. The provider may or may not have ticketed the booking.

Do not blindly retry a non-idempotent `confirmTicket` call.

Better behavior:

1. Move booking to `CONFIRMATION_PENDING` or `UNKNOWN_PROVIDER_STATUS`.
2. Query provider by `provider_hold_id`, provider reference, or correlation ID.
3. If provider supports idempotency, retry with the same idempotency key.
4. If provider does not support idempotency, reconcile before retrying a mutating call.
5. Keep payment authorized rather than captured where possible.
6. If payment was captured and provider ultimately fails, issue refund.
7. Show user: "Booking is being confirmed" with a time-bound SLA.
8. Alert if booking remains stuck beyond threshold.

### Duplicate booking requests

Use idempotency at multiple layers:

- Client to Booking Service: `Idempotency-Key`.
- Booking Service to Payment Provider: payment idempotency key.
- Booking Service to airline/GDS: provider idempotency key if supported.
- Reconciliation by provider references when idempotency is not supported.

Store:

```text
idempotency_key
request_hash
booking_id
status
expires_at
```

If the same idempotency key is reused with a different request body, return an idempotency conflict.

---

## 11. Consistency Model

| Operation | Consistency | Reason |
|---|---|---|
| Search results | Eventual | Discovery path prioritizes latency and availability |
| Price shown in search | Eventual + freshness metadata | Price can change before booking |
| Price validation before booking | Strong relative to provider | Provider is source of truth |
| Booking state transitions | Strong | Avoid duplicate bookings and invalid state transitions |
| Payment processing | Strong/idempotent | Money movement needs correctness and reconciliation |
| Historical analytics | Eventual | Analytics can tolerate lag and replay |

Important interview sentence:

> I prefer availability for search with explicit freshness metadata, but I require provider revalidation before booking because stale search data cannot be used to confirm payment or ticketing.

---

## 12. Failure Scenarios

## 12.1 Redis cache down during peak traffic

Risk:

```text
15k QPS search traffic -> OpenSearch directly -> OpenSearch overload -> search outage
```

Handling:

1. Search Service fails fast on Redis calls with short timeouts.
2. Use local in-memory hot-route cache where possible.
3. Use CDN/edge cache for anonymous popular searches where safe.
4. Protect OpenSearch using concurrency limits, rate limits, and circuit breakers.
5. Degrade by returning fewer results or disabling expensive filters/sorts.
6. Shed low-priority traffic if OpenSearch saturates.
7. Alert on Redis availability, cache hit ratio drop, OpenSearch QPS spike, and latency.

---

## 12.2 Kafka consumers lag for one large provider

Handling:

1. Mark that provider's data stale.
2. Deprioritize or suppress stale provider offers.
3. Keep healthy providers available.
4. Scale consumers up to partition count.
5. Bulk index to OpenSearch.
6. Coalesce superseded offer updates.
7. Isolate provider using provider-specific topics or partitioning.
8. Alert on lag by provider, route, and partition.

Adding partitions may help future throughput, but it may not immediately drain old backlog and can affect ordering assumptions.

---

## 12.3 OpenSearch unavailable

If Redis has cached data for popular routes:

- Return cached/precomputed results.
- Mark them as stale if needed.
- Include `freshnessTimestamp`.
- Set `requiresRevalidation=true`.

For long-tail routes with cache misses:

- Return degraded response: "temporarily unavailable, please retry."
- Avoid reckless provider fanout that violates latency and rate limits.

Booking remains safe because every offer must be revalidated with the provider before payment or ticketing.

---

## 12.4 Provider unavailable

Search behavior:

- Keep showing fresh data from healthy providers.
- Show stale provider results only within safety threshold.
- Mark stale results clearly.
- Suppress provider offers after maximum stale window.

Booking behavior:

- Do not confirm booking if provider validation/hold/confirmation is unavailable.
- Return retryable failure or `CONFIRMATION_PENDING` only when a provider call may have succeeded but response is unknown.

---

## 13. Disaster Recovery and Zero-Downtime Deployments

## 13.1 Multi-AZ design

Deploy all critical services across multiple availability zones:

```text
Load Balancer/API Gateway
 -> Search/Booking services across AZs
 -> Redis cluster across AZs
 -> Kafka across AZs
 -> OpenSearch across AZs
 -> PostgreSQL primary + standby/read replicas across AZs
```

## 13.2 Multi-region strategy

| Workload | Strategy |
|---|---|
| Search | Active-active is feasible |
| Ingestion | Active-active or active-passive depending on provider contracts |
| Booking | Start with active-passive or regional ownership |

Search can tolerate eventual consistency and regional differences. Booking is harder because payment and provider confirmation require careful consistency and reconciliation.

## 13.3 Region failure

If primary region fails:

1. Global load balancer routes traffic to healthy region.
2. Search serves from regional OpenSearch/cache.
3. Booking fails over only if PostgreSQL, payment, and provider integrations are ready.
4. In-flight ambiguous bookings are reconciled.
5. Provider webhooks must route to active region or be accepted globally and queued.

Define:

```text
RTO = how quickly service recovers
RPO = how much data loss is acceptable
```

For bookings, RPO should be close to zero. For search index/cache, RPO can be higher because they can be rebuilt.

## 13.4 PostgreSQL failover

Use:

- Primary in one AZ.
- Synchronous or semi-synchronous standby for low RPO.
- Automated failover.
- Read replicas for read traffic.
- PITR backups.
- Regular restore drills.

Avoid split-brain writes. Only one primary should accept booking writes.

## 13.5 Rebuilding OpenSearch

OpenSearch is a derived index.

Rebuild flow:

```text
Raw provider events in Kafka/object storage
 -> replay through normalizer/indexer
 -> build new OpenSearch index
 -> validate counts and freshness
 -> atomically switch alias from old index to new index
```

Use index aliases:

```text
flights_v1
flights_v2
flights_current -> flights_v2
```

## 13.6 Zero-downtime deployment

For stateless services:

- Rolling deployments.
- Blue-green deployments.
- Canary releases.
- Health checks and readiness checks.
- Fast rollback.
- Backward-compatible APIs.

For booking service:

- Graceful shutdown.
- Stop accepting new requests before shutdown.
- Finish or persist in-flight workflows.
- Resume long-running workflows from PostgreSQL state.

## 13.7 Schema migrations

Use expand-migrate-contract:

```text
1. Add nullable column/table.
2. Deploy code that writes old and new formats.
3. Backfill existing data.
4. Switch reads to new format.
5. Remove old format later.
```

For OpenSearch:

```text
new index version -> dual write or replay -> validate -> alias switch
```

---

## 14. Observability

### Search metrics

```text
search_qps
search_p50_latency
search_p95_latency
search_p99_latency
search_error_rate
search_timeout_rate
search_result_count
stale_result_percentage
```

### Cache metrics

```text
redis_latency
cache_hit_ratio
cache_evictions
cache_memory_usage
hot_key_frequency
```

### OpenSearch metrics

```text
query_latency
query_error_rate
indexing_latency
indexing_error_rate
cluster_health
shard_failures
disk_usage
```

### Kafka and ingestion metrics

```text
consumer_lag_by_provider
consumer_lag_by_partition
events_per_second
processing_latency
normalization_failures
deduplication_rate
dlq_count
freshness_lag_by_provider
freshness_lag_by_route
```

### Booking/payment metrics

```text
booking_attempts_per_second
offer_validation_success_rate
price_change_rate
hold_creation_success_rate
payment_authorization_success_rate
payment_capture_success_rate
provider_confirmation_success_rate
refund_rate
stuck_bookings_by_state
idempotency_conflict_rate
```

### Provider metrics

```text
provider_latency
provider_timeout_rate
provider_error_rate
provider_success_rate
circuit_breaker_open_count
provider_rate_limit_errors
provider_data_age_seconds
```

### Infrastructure metrics

```text
cpu
memory
network
disk
container_restarts
db_connection_pool_saturation
postgres_replication_lag
postgres_transaction_latency
kafka_broker_health
opensearch_cluster_health
```

### Logs

Use structured logs with:

```text
requestId
traceId
userId
bookingId
offerId
providerId
routeKey
operation
state
errorCode
```

Do not log sensitive PII, credentials, or raw payment details.

### Traces

Search trace:

```text
API Gateway -> Search Service -> Redis -> OpenSearch -> Ranking
```

Booking trace:

```text
API Gateway
 -> Booking Service
 -> Provider Validate
 -> Provider Hold
 -> Payment Gateway
 -> Provider Confirm
 -> PostgreSQL
```

### Alerts

Alert on:

- Search P95/P99 above SLA.
- Cache hit ratio sudden drop.
- OpenSearch error rate or cluster red/yellow.
- Kafka lag over freshness SLA.
- Provider timeout/error spike.
- Booking stuck in `CONFIRMATION_PENDING`.
- Payment success but provider failure spike.
- Refund rate spike.
- PostgreSQL replication lag.
- Database connection saturation.

---

## 15. Security

### API security

- Authenticate users and clients.
- Use API Gateway/WAF.
- Rate limit per user, IP, client, and endpoint.
- Validate request payloads.
- Use TLS everywhere.

### Provider integration security

- Store provider credentials in secrets manager.
- Rotate credentials.
- Use least privilege.
- Verify webhook signatures.
- Use timestamp and nonce replay protection.
- Use mTLS where supported.
- Apply provider-specific rate limits.

### Payment and PII

- Do not store raw card data.
- Use payment provider tokens.
- Encrypt sensitive passenger information.
- Limit access to PII.
- Maintain audit logs for booking and payment state changes.

---

## 16. Key Trade-offs

| Decision | Reason | Trade-off |
|---|---|---|
| Search from OpenSearch, not live providers | Low latency at 15k QPS | Search can be stale |
| Redis cache on search path | Absorbs repeated hot route/date searches | Cache invalidation and staleness complexity |
| Mandatory provider revalidation | Prevents stale search data from becoming incorrect booking | Adds booking latency |
| Kafka ingestion pipeline | Decouples provider updates from indexing/analytics | Operational complexity |
| PostgreSQL for bookings | ACID state transitions and idempotency | Needs careful failover and scaling |
| Snowflake/data lake for analytics | Historical queries without hurting production | Eventual analytics delay |
| Search active-active | Search is read-heavy and eventually consistent | Regional freshness differences |
| Booking active-passive first | Booking requires stronger consistency | More complex failover and lower regional write flexibility |

---

## 17. Final Reference Architecture

### 17.1 End-to-end component diagram

![Flight Aggregator Architecture Flowchart](../../docs/flight-aggregator-architecture-flowchart.png)

PNG source: `../../docs/flight-aggregator-architecture-flowchart.png`

```text
                                      +-----------------------------+
                                      | External Providers          |
                                      | Airlines / GDS / OTAs       |
                                      | Amadeus / Sabre / Airline   |
                                      +-------------+---------------+
                                                    |
                         Webhooks / Polling / APIs  |
                                                    v
+----------------+      +------------------+   +--------------------+
| Web / Mobile   |----->| CDN / Edge Cache |-->| API Gateway / WAF  |
| Clients        |      | Static + safe    |   | Auth, rate limits, |
| Partners       |      | search caching   |   | quotas, validation |
+----------------+      +------------------+   +---------+----------+
                                                             |
                 +-------------------------------------------+-------------------------------------------+
                 |                                           |                                           |
                 v                                           v                                           v
      +----------------------+                  +----------------------+                    +----------------------+
      | Search Service       |                  | Booking Service      |                    | Provider Connector   |
      | Query normalization  |                  | Booking state        |                    | Provider adapters   |
      | Ranking orchestration|                  | Idempotency          |                    | Polling/webhooks    |
      +----------+-----------+                  +----------+-----------+                    +----------+-----------+
                 |                                         |                                           |
      +----------+-----------+                             |                                           v
      |                      |                             |                                +----------------------+
      v                      v                             |                                | Ingestion Service    |
+-------------+      +------------------+                  |                                | Validate, dedupe,    |
| Redis Cache |      | OpenSearch       |                  |                                | canonicalize events |
| Hot route   |      | Search index     |                  |                                +----------+-----------+
| results     |      | Derived read     |                  |                                           |
+-------------+      | model            |                  |                                           v
                     +---------+--------+                  |                                +----------------------+
                               ^                           |                                | Kafka / Event Bus    |
                               |                           |                                | provider-updates    |
                               |                           |                                +----------+-----------+
                               |                           |                                           |
                               |                           |                         +-----------------+------------------+
                               |                           |                         |                                    |
                               |                           v                         v                                    v
                     +---------+--------+        +----------------------+   +----------------------+          +----------------------+
                     | Search Index     |        | PostgreSQL Booking   |   | Stream Processor     |          | Raw Event Storage    |
                     | Updater          |        | DB                   |   | Normalize, version,  |          | Object storage       |
                     +---------+--------+        | ACID state machine   |   | coalesce, enrich     |          +----------+-----------+
                               ^                 +----------+-----------+   +----------+-----------+                     |
                               |                            |                          |                                 v
                               |                            v                          v                      +----------------------+
                               |                 +----------------------+   +----------------------+          | Snowflake / Data     |
                               |                 | Payment Gateway      |   | OpenSearch Index     |          | Warehouse            |
                               |                 | Authorize/capture    |   | Writer               |          | Analytics            |
                               |                 +----------+-----------+   +----------+-----------+          +----------------------+
                               |                            |
                               |                            v
                               |                 +----------------------+
                               +-----------------| Airline/GDS Booking |
                                                 | APIs                |
                                                 | Validate, hold,     |
                                                 | confirm/ticket      |
                                                 +----------------------+
```

### 17.2 Search read path

```text
User search request
  -> CDN / API Gateway
  -> Search Service
  -> Normalize query and build cache key
  -> Redis lookup
       -> cache hit:
            return cached ranked results with freshness metadata
       -> cache miss:
            query OpenSearch by route/date/cabin
            rank, filter, deduplicate provider offers
            write short-lived cache entry
            return results with freshness metadata
```

Key rule:

> The search path does not call airline APIs synchronously. This keeps p95 latency low and isolates users from provider outages.

### 17.3 Provider ingestion and indexing path

```text
Provider webhook / polling response / stream event
  -> Provider Connector
  -> Ingestion Service
  -> Validate schema and provider signature
  -> Deduplicate event
  -> Publish to Kafka
  -> Stream Processor
       -> normalize provider payload
       -> reject stale event versions
       -> coalesce superseded price updates
       -> enrich route/date/provider metadata
  -> Search Index Updater
  -> OpenSearch
  -> Redis invalidation or async cache refresh
  -> Raw object storage
  -> Snowflake/Data Warehouse
```

Key rule:

> OpenSearch and Redis are derived serving layers. They can be rebuilt from durable provider events and object storage.

### 17.4 Booking state and payment/provider coordination

```text
User selects offer from search
  -> POST /v1/bookings with Idempotency-Key
  -> Booking Service creates booking_id = INITIATED
  -> Validate latest price and availability with provider
       -> price changed: return PRICE_CHANGED
       -> unavailable: return OFFER_EXPIRED
  -> Create provider hold/reservation
       -> store provider_hold_id and hold_expires_at
  -> Authorize payment
       -> payment failed: release provider hold
  -> Confirm/ticket with provider
       -> provider confirmed: capture payment and mark CONFIRMED
       -> provider failed: void/refund payment and mark PROVIDER_FAILED
       -> provider timeout: mark CONFIRMATION_PENDING and reconcile
  -> Notify user
  -> Emit booking events for audit, notification, and reconciliation
```

### 17.5 Failure compensation summary

| Failure | System behavior |
|---|---|
| Payment succeeds, provider confirmation fails | Void authorization or refund captured payment; mark `PROVIDER_FAILED` or `REFUND_PENDING` |
| Provider hold succeeds, payment fails | Let user retry until hold expiry; otherwise release/cancel hold |
| Provider confirmation times out | Mark `CONFIRMATION_PENDING` or `UNKNOWN_PROVIDER_STATUS`; query provider before retrying non-idempotent confirmation |
| Duplicate booking request | Use `Idempotency-Key` to return the same booking result |
| OpenSearch unavailable | Serve cached popular routes with stale metadata; long-tail routes return degraded retry response |
| Redis unavailable | Fail fast, use local hot-route cache if possible, protect OpenSearch with rate/concurrency limits |
| Kafka consumer lag | Mark affected provider/route stale, scale consumers, coalesce updates, alert on freshness SLA |

---

## 18. Interview Communication Template

Use this format for major design decisions:

> I chose X because the requirement is A and the workload is B. The trade-off is C. If requirement D changes, I would consider Y.

Examples:

> I chose OpenSearch because flight search needs multi-field filtering, sorting, and ranking at high QPS. The trade-off is eventual consistency and operational complexity. I avoid correctness issues by revalidating every offer before booking.

> I chose Redis because repeated route/date searches need low latency and hot-route protection. The trade-off is stale data and invalidation complexity, so I use short TTLs, freshness metadata, request coalescing, and mandatory booking revalidation.

> I chose PostgreSQL for bookings because booking state transitions, idempotency keys, and payment/provider references require ACID transactions. The trade-off is that scaling writes is harder than with NoSQL, but booking QPS is much lower than search QPS.

---

## 19. Topics to Review

1. Booking/payment saga design.
2. Idempotency and reconciliation.
3. Search caching and stale-while-revalidate.
4. Kafka partitioning and consumer lag handling.
5. OpenSearch index design and alias-based reindexing.
6. Multi-region active-active versus active-passive trade-offs.
7. Zero-downtime schema and index migrations.
8. Provider outage handling and circuit breakers.
9. Observability for freshness, booking correctness, and provider health.

---

## 20. Final Summary

The platform separates the system into three major paths:

1. **Search path**: optimized for low latency, high QPS, caching, OpenSearch, and eventual consistency.
2. **Ingestion path**: optimized for asynchronous provider updates, Kafka buffering, normalization, deduplication, indexing, and historical analytics.
3. **Booking path**: optimized for correctness, provider revalidation, temporary holds, payment coordination, idempotency, and reconciliation.

The most important design decision is not to use live provider calls in the normal search path. Instead, provider data is continuously ingested and indexed. Search stays fast and available, while booking remains safe through mandatory provider revalidation and strong booking state management.
