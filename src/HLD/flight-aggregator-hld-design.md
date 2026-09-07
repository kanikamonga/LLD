# High-Level Design: Flight Aggregator Platform

## 1. Problem Statement

Design a flight aggregation platform that collects flight schedules, prices, and availability from multiple airlines and travel agencies and serves end users who search for flights.

The platform must:

- Search flights by origin, destination, and travel date.
- Support one-way and round-trip searches.
- Aggregate schedules, fares, availability, baggage, cancellation rules, and fare families.
- Keep prices and availability reasonably fresh.
- Handle thousands of search queries per second with low latency.
- Tolerate failures or delays from individual providers.
- Preserve historical flight data for analytics.
- Scale all services horizontally.
- Provide monitoring, logging, and alerting.

## 2. Scope

### In scope

- Flight search and comparison.
- One-way and round-trip itineraries.
- Provider data ingestion through webhooks, polling, or streams.
- Price and availability freshness tracking.
- Search ranking and pagination.
- Provider-specific offers and deep links.
- Historical data storage and analytics pipelines.
- Resilience, caching, replication, failover, and observability.

### Out of scope

- Platform-owned booking.
- Payment processing.
- Reservation locking.
- Ticket issuance.
- Refunds and cancellations.
- Multi-city searches.
- User registration and profile management.

The airline or travel agency owns the final booking workflow. The platform may perform a final revalidation and redirect the user to the provider, but cannot guarantee the final price after redirection.

## 3. Requirements

### Functional requirements

1. Search by:
   - Origin airport or city.
   - Destination airport or city.
   - Departure date.
   - Optional return date.
   - Passenger count.
   - Cabin class.
   - Currency.
   - Filters and sorting.
2. Support one-way and round-trip searches.
3. Aggregate results from many providers.
4. Show:
   - Flight schedules.
   - Duration.
   - Stops.
   - Prices.
   - Taxes and currency.
   - Baggage allowance.
   - Cancellation and fare rules.
   - Availability.
   - Provider and deep link.
   - Last-updated timestamp.
5. Revalidate price and availability before redirecting to a provider when supported.
6. Return partial results if one or more providers are unavailable.
7. Store raw and normalized provider data.
8. Retain historical data for analytics.

### Non-functional requirements

| Requirement | Target or decision |
|---|---|
| Daily active users | 1 million |
| Searches per active user | 50/day |
| Searches per day | 50 million |
| Average search QPS | approximately 580 |
| Peak search QPS | approximately 2,900, assuming 5x peak |
| Providers | approximately 1,000 |
| Normal provider freshness | 30-60 seconds |
| Search latency | P95 below 300 ms |
| Availability | 99.99% |
| Consistency | Eventual for search results, bounded by freshness SLA |
| Durability | Provider events must be replayable |
| Scaling | Horizontal scaling for gateways, services, consumers, and indexes |

### Capacity calculation

```text
Daily searches = 1M DAU × 50 searches/user/day = 50M searches/day
Average QPS = 50M / 86,400 ≈ 580 QPS
Peak QPS = 580 × 5 ≈ 2,900 QPS
```

A single user search must not synchronously fan out to 1,000 providers. Provider data is continuously ingested and indexed asynchronously. Search reads the platform's local search index and uses provider revalidation only when necessary.

## 4. API Design

### Search API

```http
GET /v1/flights/search
```

Example:

```text
/v1/flights/search
  ?origin=DEL
  &destination=LHR
  &tripType=ROUND_TRIP
  &departureDate=2026-10-10
  &returnDate=2026-10-20
  &passengers=2
  &cabin=ECONOMY
  &currency=INR
  &sortBy=PRICE
  &cursor=opaque-cursor
  &limit=50
```

### API decisions

- Use `origin` and `destination`, normalized to airport or city codes.
- Use `departureDate` and conditional `returnDate`.
- Use `tripType=ONE_WAY` or `ROUND_TRIP`.
- Use an opaque cursor rather than a page number. Cursor pagination is more stable when results change.
- Include an explicit limit.
- Normalize query fields before building cache keys.
- `GET` is appropriate because search does not modify server state.
- Authenticate aggregators and apply per-client rate limits at the gateway.

### Response shape

```json
{
  "requestId": "req-123",
  "results": [
    {
      "itineraryKey": "itin-456",
      "segments": [
        {
          "operatingCarrier": "AI",
          "marketingCarrier": "AI",
          "flightNumber": "AI123",
          "origin": "DEL",
          "destination": "LHR",
          "departure": "2026-10-10T02:30:00Z",
          "arrival": "2026-10-10T10:30:00Z",
          "durationMinutes": 480,
          "stops": 0
        }
      ],
      "offers": [
        {
          "provider": "airline-a",
          "offerId": "offer-789",
          "price": 42000,
          "currency": "INR",
          "baggage": "1 checked bag",
          "availability": 3,
          "lastUpdatedAt": "2026-09-06T10:00:00Z",
          "freshness": "FRESH",
          "bookingUrl": "https://provider.example/..."
        }
      ]
    }
  ],
  "nextCursor": "opaque-cursor",
  "partialResults": false,
  "providerStatus": []
}
```

### Provider failure behavior

Use a combination of:

1. Strict per-provider timeouts.
2. Provider-specific circuit breakers.
3. Fresh indexed data.
4. Cached fallback within a maximum stale window.
5. Partial results from healthy providers.
6. Clear `lastUpdatedAt` and freshness status.
7. Asynchronous retry and alerting.

The platform must not fail the entire search because one provider is slow or unavailable.

## 5. High-Level Architecture

```text
Users / Aggregator Clients
          |
          v
     Global DNS / Anycast
          |
          v
    WAF / API Gateway
    - Authentication
    - Rate limiting
    - Request validation
    - Client quotas
          |
          v
      Load Balancer
          |
    +-----+-----------------------+
    |                             |
    v                             v
 Search Service Fleet       Provider Webhook Gateway
    |                             |
    |                             v
    |                       Provider Adapters
    |                       - Webhooks
    |                       - Polling
    |                       - Streams
    |                             |
    |                             v
    |                         Kafka/Event Bus
    |                             |
    |                       Ingestion Consumers
    |                       - Validate
    |                       - Deduplicate
    |                       - Normalize
    |                       - Version-check
    |                             |
    +--------------+--------------+
                   |
          +--------+---------+
          |                  |
          v                  v
   Elasticsearch/OpenSearch  Durable normalized store
          |                  |
          v                  v
       Redis cache       Raw data/object storage
                             |
                             v
                    Historical analytics pipeline
```

### Main components

#### API Gateway / WAF

- Authenticates external aggregator clients.
- Applies client and endpoint rate limits.
- Validates requests.
- Routes traffic to stateless services.
- Protects the platform from abuse and malformed traffic.

#### Load balancer

Distributes search traffic across many stateless Search Service instances and removes unhealthy instances.

#### Search Service

- Normalizes queries.
- Checks Redis.
- Queries Elasticsearch on a miss.
- Applies filters, ranking, and pagination.
- Adds freshness information.
- Returns partial-result metadata.

#### Provider Webhook Gateway

Receives provider callbacks, verifies signatures, deduplicates events, validates schemas, and publishes accepted updates to Kafka.

#### Provider Adapters

Each provider may expose a different integration style. Adapters isolate those differences:

- Authentication.
- API shape.
- Polling schedule.
- Webhook handling.
- Provider rate limits.
- Retry policy.
- Circuit breaker state.
- Health metrics.

#### Polling Scheduler

Polls providers that do not support webhooks or streams. Polling intervals should depend on provider capability, route importance, and freshness requirements.

#### Kafka/Event Bus

- Buffers provider updates.
- Decouples ingestion from search indexing.
- Allows replay.
- Supports independent consumers for search, analytics, auditing, and monitoring.

#### Ingestion Consumers

- Validate and normalize provider events.
- Deduplicate events.
- Reject stale versions.
- Persist durable normalized data.
- Update the search index.
- Trigger cache invalidation.

#### Elasticsearch/OpenSearch

Derived read model optimized for:

- Origin/destination filters.
- Date filters.
- Price and duration sorting.
- Stops and cabin filters.
- Facets and ranking.

It is not the source of truth. It can be rebuilt from durable events and normalized storage.

#### Redis

Caches complete normalized search responses. It is not the primary flight database.

#### Durable storage

Use a durable store for normalized records, raw provider responses, and event history. Raw immutable provider payloads are often cheaper in object storage than in MongoDB; MongoDB remains reasonable when document-level querying is required.

#### Analytics platform

Use object storage/data lake plus Spark or a warehouse for:

- Historical price trends.
- Provider quality.
- Availability analysis.
- Search-to-click analytics.
- Freshness and outage analysis.

## 6. Storage Strategy

| Data | Suggested store | Reason |
|---|---|---|
| Search read model | Elasticsearch/OpenSearch | Multi-field filtering, sorting, ranking, faceting |
| Raw provider payloads | Object storage, optionally MongoDB | Immutable archive and replay; document flexibility where needed |
| Normalized durable data | Replicated database or durable key/value store | Source for rebuilding indexes |
| Historical data | Data lake/object storage + Spark/warehouse | Cheap retention and analytical scans |
| Search results | Redis | Low-latency repeated query responses |
| Provider configuration/health | PostgreSQL or configuration store | Consistent operational metadata |
| Events | Kafka with retention | Replay, decoupling, and multiple consumers |

### Elasticsearch document model

Index fields around the main search access pattern:

```text
origin
destination
departureDate
returnDate
carrier
flightNumber
segments
duration
stops
fare
currency
baggage
fareRules
availability
provider
lastUpdatedAt
version
```

Rapidly changing fare and availability data may be modeled separately from stable schedule data or represented as versioned offer documents to avoid unnecessary full-document rewrites.

## 7. Search Cache Design

### Cache key

Equivalent requests must normalize to the same key:

```text
flight-search:v1:
DEL:LHR:
ROUND_TRIP:
2026-10-10:2026-10-20:
adults=2:children=0:
cabin=ECONOMY:
currency=INR:
sort=PRICE:
filters_hash=abc123
```

Normalize:

- Uppercase airport codes.
- Canonical date format.
- Deterministic filter ordering.
- Default values.
- Currency.
- Passenger ordering.
- Sort and pagination representation.

### Value

Store the already filtered and ranked result as compressed JSON:

```json
{
  "results": [],
  "nextCursor": "opaque-cursor",
  "generatedAt": "2026-09-06T10:00:00Z",
  "freshness": "FRESH"
}
```

### Search cache flow

```text
Client
  -> API Gateway
  -> Search Service
  -> Normalize query
  -> Redis lookup
       -> hit: return cached response
       -> miss:
            -> Elasticsearch
            -> filter/rank/page
            -> cache result
            -> return response
```

Redis should not be used as the main filtering engine. Elasticsearch performs filtering and ranking; Redis caches the completed response.

### TTL and event invalidation

Use both:

- **Event-driven invalidation** to remove affected results quickly after a provider update.
- **TTL expiration** as a safety net if an invalidation event is delayed or lost.

The trade-off is that invalidation creates cache misses and can increase Elasticsearch load. Use short TTL jitter and request coalescing/single-flight to avoid a cache stampede.

### Reverse-index invalidation

When caching a result, maintain a reverse index:

```text
flight:AI123:cache-keys
  -> search-key-1
  -> search-key-2
  -> search-key-3
```

When the flight changes:

1. Read the reverse-index set.
2. Batch-delete affected search keys.
3. Remove or refresh the reverse index.

Add TTLs to reverse-index entries. For extremely popular flights with millions of dependent keys, use versioned namespaces:

```text
flight-version:AI123 = 42
```

New cache keys include version 42. Incrementing the version makes old results unreachable without deleting every key immediately.

## 8. Search Ranking and Deduplication

### Deterministic ranking

Use hard filters first:

- Route.
- Dates.
- Passenger count.
- Cabin.
- Maximum stops.

Then apply user-selected sorting:

- Cheapest.
- Fastest.
- Earliest departure.

Default ranking can use a versioned weighted score:

```text
score =
  0.45 × normalized_price_score
+ 0.30 × normalized_duration_score
+ 0.15 × stops_score
+ 0.10 × provider_quality_score
```

Use deterministic tie-breakers:

```text
score DESC
price ASC
duration ASC
departureTime ASC
offerId ASC
```

Determinism improves caching, debugging, pagination stability, and reproducibility.

### Canonical flight identity

Do not deduplicate using only flight number. Use:

```text
operatingCarrier
marketingCarrier
flightNumber
departureAirport
arrivalAirport
scheduledDepartureDateTime
```

Example:

```text
AI|AI123|DEL|LHR|2026-10-10T02:30Z
```

For round trips, combine outbound and inbound segment keys into an itinerary key.

### Provider offers

Deduplicate at the canonical flight/itinerary level, but preserve each provider-specific offer:

```text
Canonical flight: AI123 DEL -> LHR

Offers:
- Airline API: INR 42,000, one checked bag
- Agency A: INR 41,500, no checked bag
- Agency B: INR 43,000, refundable
```

Do not average or overwrite conflicting prices. Rank offers separately and show provider, terms, freshness, availability, and deep link.

## 9. Provider Ingestion

```text
Provider Webhooks ----+
Provider Polling -----+--> Provider Adapters --> Kafka
Provider Streams -----+
```

### Webhooks

- Verify signature.
- Validate timestamp and nonce.
- Reject replayed events.
- Validate schema and size.
- Deduplicate.
- Publish to Kafka.
- Acknowledge only after durable acceptance.

### Polling

- Provider-specific schedule.
- Exponential backoff.
- Circuit breaker.
- Provider rate limits.
- Independent connection pools.

### Kafka topic strategy

Prefer a shared topic with a stable partition key:

```text
topic: flight-updates
partition key: providerId + canonicalFlightKey
```

This preserves ordering for one provider's flight while allowing different flights to process in parallel.

Separate topics can be justified for providers with unique retention, compliance, or isolation requirements, but one topic per provider creates operational overhead at 1,000 providers.

## 10. Event Ordering, Retries, and Idempotency

### Consumer processing

```text
Read event
  -> validate
  -> check ID/version
  -> durable normalized write
  -> Elasticsearch update
  -> Redis invalidation
  -> commit Kafka offset
```

Commit only after successful processing. If a consumer crashes before committing, Kafka redelivers the event.

### Stale event protection

If events arrive as `v10`, `v12`, `v11`, the consumer must discard `v11` after `v12` has been applied:

```text
if incoming.version <= stored.version:
    ignore
else:
    apply update
```

The comparison and update must be atomic:

```sql
UPDATE flight_offer
SET version = 12, price = ...
WHERE offer_id = ?
  AND version < 12;
```

Prefer:

1. Provider sequence number.
2. Provider event version.
3. Provider timestamp with deterministic tie-breaker.
4. Ingestion timestamp only as a last resort.

### At-least-once delivery

Use at-least-once delivery plus idempotent consumers and versioned writes. End-to-end exactly-once behavior is difficult across Kafka, databases, Elasticsearch, and Redis.

### Retry topics

Transient failures such as Elasticsearch or database timeouts should use exponential backoff and jitter:

```text
1s -> 5s -> 30s -> 2m -> 10m
```

Permanent failures such as invalid schemas should go to a dead-letter topic. Retry topics prevent a poison message from blocking a whole partition.

Monitor:

- Consumer lag.
- Retry backlog.
- DLQ volume.
- Event processing latency.
- Out-of-order events.
- Duplicate events.
- Indexing failures.

## 11. Provider Outage Handling

When a provider is unavailable:

1. Open its circuit breaker.
2. Stop repeatedly calling the failing provider.
3. Keep existing indexed data temporarily.
4. Use cached/indexed data only within the maximum stale window.
5. Mark results with `lastUpdatedAt` and a stale warning.
6. Return partial results from healthy providers.
7. Perform controlled half-open health checks.
8. Alert on provider data age and outage duration.

Freshness policy:

| Data age | Behavior |
|---|---|
| Under 60 seconds | Show normally |
| 1-5 minutes | Show with stale warning |
| Beyond safety threshold | Hide or return as unavailable |

Do not silently present old data as current.

## 12. Multi-Region Availability and Disaster Recovery

```text
Global DNS / Anycast
       /       \
      v         v
 Region A     Region B
 +---------+  +---------+
 | Gateway |  | Gateway |
 | Search  |  | Search  |
 | Redis   |  | Redis   |
 | Search  |  | Search  |
 | Index   |  | Index   |
 +---------+  +---------+
       \       /
        Durable replicated data/events
```

### Design

- Route users to the nearest healthy region.
- Keep gateway and search services stateless.
- Use local Redis caches; do not require synchronous cross-region cache replication.
- Maintain regional Elasticsearch read models.
- Replicate durable events and normalized data.
- Rebuild Redis and Elasticsearch from durable data when necessary.
- Use idempotent consumers for replicated events.

### Failover

```text
Region A fails
  -> health checks detect failure
  -> traffic routes to Region B
  -> Region B serves local index
  -> Redis misses rehydrate from Elasticsearch
  -> freshness metadata controls visible results
```

Example targets:

```text
RTO: 5 minutes
RPO: less than 1 minute of ingestion events
```

### Regional staleness

If Region B is five minutes behind, continue serving when availability is more important than exact freshness, but label the data. If it exceeds the maximum stale threshold, trigger refresh or return partial/degraded results rather than presenting stale prices as current.

## 13. Observability

### Search metrics

```text
search_requests_total
search_errors_total
search_latency_ms
search_results_count
search_partial_response_total
search_stale_response_total
```

Track P50, P95, and P99 latency.

### Redis metrics

```text
cache_hits_total
cache_misses_total
cache_hit_ratio
cache_evictions_total
cache_memory_usage
cache_latency
```

### Elasticsearch metrics

```text
query_latency
search_errors
indexing_latency
indexing_failures
cluster_health
shard_failures
disk_usage
```

### Kafka and ingestion metrics

```text
consumer_lag
event_processing_latency
retry_count
dead_letter_events
out_of_order_events
duplicate_events
```

### Provider metrics

```text
provider_request_rate
provider_error_rate
provider_latency
circuit_breaker_state
last_successful_update
provider_data_age_seconds
polling_failures
webhook_failures
```

### Logs

Use structured logs with:

```text
requestId
traceId
providerId
flightKey
region
operation
status
errorCode
```

Do not log credentials or unnecessary personal data.

### Tracing

Trace search requests through:

```text
API Gateway -> Search Service -> Redis -> Elasticsearch
```

Trace ingestion through:

```text
Provider Adapter -> Kafka -> Consumer -> Durable Store
  -> Elasticsearch -> Redis invalidation
```

### Alerts and SLOs

Alert on:

- Search P95 above 300 ms.
- Search error rate.
- Redis hit ratio collapse.
- Kafka lag exceeding freshness window.
- Provider data age exceeding SLA.
- Elasticsearch indexing failures.
- DLQ spikes.
- Circuit breakers remaining open.
- Regional health failure.
- Stale-result percentage increasing.

Example SLOs:

```text
99.99% search availability
P95 search latency < 300 ms
99% provider data refreshed within 60 seconds
Less than 1% stale-result responses
Less than 0.1% events entering the DLQ
```

## 14. Security

### Provider credentials

Do not store credentials directly in the application database. Use a managed secrets manager:

- KMS/HSM-backed encryption.
- Least-privilege access per adapter.
- In-memory short-lived credential caching.
- Rotation according to provider contract.
- Secret access audit logs.
- No credentials in logs or exceptions.

### Webhook security

Use:

- Provider-specific HMAC signatures.
- Timestamp and nonce replay protection.
- mTLS where supported.
- IP restrictions as an additional layer.
- Schema and payload-size validation.
- Provider-specific rate limits.
- WAF/API Gateway protection.

Example:

```text
signature = HMAC-SHA256(secret, timestamp + "." + requestBody)
```

If a provider does not supply an event ID, use a deterministic fingerprint, such as:

```text
hash(providerId + eventType + flightKey + version + payloadHash)
```

### Webhook acknowledgment

Return success only after the event is durably accepted. Otherwise, the provider may stop retrying while the platform has lost the update.

## 15. Key Trade-offs

### Elasticsearch versus relational database

Elasticsearch is preferred for the serving read model because flight search requires multi-field filtering, sorting, and ranking. The trade-off is operational complexity and eventual consistency. A durable source store remains necessary.

### Redis cache versus direct Elasticsearch

Redis reduces latency and protects Elasticsearch for repeated queries. The trade-off is stale results and invalidation complexity. Short TTLs, event invalidation, and freshness metadata bound the risk.

### Reverse-index invalidation versus versioned namespaces

Reverse indexes provide immediate deletion of affected cache entries. The trade-off is large dependency sets and invalidation fan-out. Versioned namespaces avoid mass deletion but leave unreachable entries until TTL cleanup.

### Eventual consistency versus strong consistency

Search results can be eventually consistent because this is not the booking commit path. Prices and availability require a bounded freshness SLA and explicit timestamps. Strong consistency would increase latency and reduce availability without guaranteeing the provider's final booking price.

### Regional indexes versus one global index

Regional indexes reduce search latency and isolate failures. The trade-off is temporary regional differences and replication lag. Durable events permit replay and recovery.

### At-least-once versus exactly-once processing

At-least-once is more resilient and practical. Duplicates are handled with event IDs, deterministic document IDs, and versioned conditional writes.

## 16. Complete Critical Flows

### Search cache hit

```text
Client
 -> API Gateway
 -> authenticate/rate-limit
 -> Search Service
 -> normalize request
 -> Redis hit
 -> return cached results + freshness metadata
```

### Search cache miss

```text
Client
 -> API Gateway
 -> Search Service
 -> Redis miss
 -> Elasticsearch query
 -> filter/rank/deduplicate
 -> cache result and reverse dependencies
 -> return results
```

### Provider webhook

```text
Provider
 -> Webhook Gateway
 -> verify HMAC/timestamp/nonce
 -> deduplicate event
 -> validate schema
 -> Kafka
 -> immediate success response
 -> Ingestion Consumer
 -> version-aware durable write
 -> Elasticsearch update
 -> Redis invalidation
 -> analytics consumers
```

### Provider polling

```text
Scheduler
 -> Provider Adapter
 -> circuit breaker/timeout/rate limit
 -> provider API
 -> canonical event
 -> Kafka
 -> same ingestion pipeline
```

### Elasticsearch failure

```text
Provider event
 -> Kafka retained
 -> durable normalized write succeeds
 -> Elasticsearch update fails
 -> retry topic/backoff
 -> repeated failure -> DLQ
 -> alert
 -> replay after recovery
```

Do not invalidate Redis until the new search document is indexed successfully.

## 17. Topics to Study in Detail for Interviews

### Highest priority

1. **Distributed caching**
   - Cache-aside.
   - TTL design and jitter.
   - Event-driven invalidation.
   - Reverse indexes.
   - Versioned cache namespaces.
   - Cache stampede and request coalescing.
   - Hot keys and eviction.

2. **Kafka fundamentals and operations**
   - Topics, partitions, brokers, replication.
   - Partition-key selection.
   - Ordering guarantees.
   - Consumer groups and rebalancing.
   - Offset commits.
   - At-least-once delivery.
   - Retry topics and DLQs.
   - Consumer lag.
   - Exactly-once limitations.

3. **Elasticsearch/OpenSearch**
   - Index and document design.
   - Shards and replicas.
   - Refresh intervals.
   - Query/filter performance.
   - Sorting and pagination.
   - Deep pagination and search-after.
   - Reindexing and alias-based zero-downtime migration.
   - Hot shards and cluster sizing.

4. **Flight search data modeling**
   - Canonical flight identity.
   - Itinerary and segment modeling.
   - Provider-specific offer modeling.
   - Schedule data versus volatile fare/availability.
   - Time zones and date normalization.
   - Codeshares and flight-number reuse.

5. **Distributed consistency**
   - Eventual versus strong consistency.
   - Versioned writes.
   - Optimistic concurrency.
   - Out-of-order events.
   - Idempotency.
   - Stale data windows.
   - Source of truth versus derived indexes.

### Provider integration and reliability

6. **Circuit breakers**
   - Closed, open, and half-open states.
   - Timeouts and backoff.
   - Provider isolation.
   - Partial failure.

7. **Webhook security**
   - HMAC signatures.
   - mTLS.
   - Replay protection.
   - Nonces and timestamps.
   - Provider event deduplication.

8. **Polling and streaming ingestion**
   - Adaptive polling.
   - Provider rate limits.
   - Backpressure.
   - Webhooks versus polling trade-offs.

9. **Retry and failure patterns**
   - Exponential backoff with jitter.
   - Poison messages.
   - Dead-letter queues.
   - Replay.
   - Outbox and durable acceptance.

### Scale and availability

10. **Multi-region architecture**
    - Global routing.
    - Active-active versus active-passive.
    - Regional read models.
    - RTO and RPO.
    - Cross-region event replication.
    - Disaster recovery testing.

11. **Capacity estimation**
    - QPS and peak factors.
    - Active working set.
    - Index size and shard sizing.
    - Cache memory.
    - Provider fan-out avoidance.
    - Storage growth and retention.

12. **Horizontal scaling**
    - Stateless services.
    - Load balancing.
    - Autoscaling signals.
    - Backpressure and admission control.
    - Zero-downtime deployment.

13. **Rate limiting**
    - Per-client quotas.
    - Token bucket and sliding windows.
    - Provider-specific limits.
    - Fairness and noisy-neighbor isolation.

### Production engineering

14. **Observability**
    - RED metrics: rate, errors, duration.
    - Consumer lag.
    - Data freshness SLOs.
    - Distributed tracing.
    - High-cardinality metrics.
    - Alert quality and runbooks.

15. **Security**
    - Secrets management.
    - KMS encryption.
    - Least privilege.
    - API authentication.
    - WAF and abuse prevention.
    - PII and sensitive-data handling.

16. **Cost and operations**
    - Elasticsearch storage and shard cost.
    - Redis memory cost.
    - CDN and network egress.
    - Raw-data retention tiers.
    - Provider API cost and quotas.

## 18. Interview Communication Template

For every major design choice, use:

> “I chose X because the requirement is A and the workload is B. The trade-off is C. I would reconsider X if requirement D changed, and then I would use Y.”

Examples:

> “I chose Elasticsearch because flight search needs multi-field filtering, sorting, and ranking. The trade-off is operational complexity and eventual consistency. I would use a relational database instead if the workload required transactional booking state, but booking is out of scope here.”

> “I chose Redis because repeated normalized searches need low latency. The trade-off is stale cached prices and invalidation complexity. I use event invalidation plus TTL and would bypass the cache after the freshness threshold.”

> “I use at-least-once Kafka delivery because replay and resilience are more important than end-to-end exactly-once behavior. The trade-off is duplicate events, handled by idempotency keys and versioned writes.”

## 19. Final Design Summary

The recommended architecture is a multi-region, stateless search platform with:

- API Gateway, WAF, authentication, and rate limiting.
- Horizontally scaled Search Service instances.
- Provider adapters for webhooks, polling, and streams.
- Kafka as the durable event backbone.
- Version-aware, idempotent ingestion consumers.
- Elasticsearch as a derived search index.
- Redis for normalized result caching.
- Reverse-index or versioned cache invalidation.
- Durable raw and normalized data for replay and analytics.
- Provider-specific circuit breakers and freshness policies.
- Regional indexes and caches with global failover.
- Structured logs, distributed tracing, freshness metrics, and SLO-based alerts.

The central design principle is:

> Keep provider-specific integration and failures isolated, keep the search path local and fast, and treat freshness as an explicit measurable property rather than assuming every displayed price is current.

