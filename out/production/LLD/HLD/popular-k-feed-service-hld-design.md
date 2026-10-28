# High-Level Design: Popular-K Feed Service for Confluence

## Original Problem Statement

Design a "Popular-K" feed for Confluence that surfaces the top `K` most popular content items such as pages, blog posts, and comments across one or more scopes and time windows.

Example scopes:

- Site-wide.
- By space.
- By owner team.

Example time windows:

- Last 15 minutes.
- Last 24 hours.
- Last 7 days.

The feature appears on user dashboards and space home pages, updating near real-time as engagement events stream in:

- Views.
- Likes/reactions.
- Comments.
- Shares.

The system must be product-agnostic and extensible so additional Atlassian products such as Jira issues and Bitbucket pull requests can contribute content and signals without a redesign.

Topics:

- High-Level Design.
- Top-K.
- Streaming data.
- Caching.
- Indexing.
- Real-time systems.
- Data consistency.
- Eventual consistency.
- Batch processing.
- Cron jobs.

---

## 1. Design Principle

The central design principle is:

> Use streaming aggregation for near-real-time Popular-K, backed by durable event storage, windowed counters, cacheable feed materialization, and product-agnostic content/event schemas.

Popularity can be eventually consistent, but **permission checks must be strongly enforced before returning content to a user**.

---

## 2. Requirements

### Functional requirements

1. **Compute top K popular content**
   - Return most popular content items by score.
   - Support multiple content types:
     - Confluence page.
     - Blog post.
     - Comment.
     - Future: Jira issue, Bitbucket pull request, Atlas project, etc.

2. **Support scopes**
   - Site-wide.
   - Space-level.
   - Team-owned content.
   - User-owned content.
   - Product-specific scopes in future.

3. **Support time windows**
   - Last 15 minutes.
   - Last 1 hour.
   - Last 24 hours.
   - Last 7 days.
   - Potential custom windows later.

4. **Ingest engagement events**
   - View.
   - Like/reaction.
   - Comment.
   - Share.
   - Bookmark.
   - Other product-specific signals.

5. **Rank by weighted popularity**
   - Views may count less than likes, comments, or shares.
   - Recent events should matter more for short windows.

6. **Expose feed API**
   - Query by tenant/site, scope, window, content type, and limit `K`.

7. **Extensible across products**
   - Jira, Bitbucket, Confluence, and future products should emit normalized events.
   - Popular-K service should not depend on Confluence-specific internals.

8. **Handle permissions**
   - Users should not see content they are not allowed to access.

### Non-functional requirements

| Requirement | Target |
|---|---:|
| Feed latency | P95 < 100-200 ms |
| Feed freshness | Seconds to 1-2 minutes |
| Availability | 99.99% |
| Event ingestion | High throughput |
| Consistency | Eventual consistency acceptable for popularity |
| Ranking correctness | Approximate top K acceptable for real-time |
| Durability | Engagement events should not be lost |
| Multi-tenancy | Strong tenant isolation |
| Extensibility | Product-agnostic event and content model |
| Scalability | Millions of tenants/content items/events |

---

## 3. Clarifications and Assumptions

### Popularity is eventually consistent

The feed does not need strict consistency. If a page receives a like, it does not need to appear instantly at rank 1. A delay of seconds to a couple of minutes is acceptable.

### Permission filtering is required

A globally popular page may not be visible to every user. The system must avoid leaking restricted content.

Options:

1. Precompute feeds without user-specific permissions, then filter at request time.
2. Precompute personalized permission-aware feeds.

For v1:

> Precompute scope-level Popular-K feeds, then apply permission filtering at read time using a content permission service.

### Top-K can be approximate

At large scale, exact top K over all events for every scope/window can be expensive.

Use streaming approximations or bucketed counters where needed.

---

## 4. Core Concepts

## 4.1 Scope

A scope defines the aggregation boundary.

Examples:

```text
tenant:{tenantId}:site
tenant:{tenantId}:space:{spaceId}
tenant:{tenantId}:team:{teamId}
tenant:{tenantId}:owner:{ownerId}
tenant:{tenantId}:product:{productKey}
```

## 4.2 Window

A time window defines recency.

Examples:

```text
15m
1h
24h
7d
```

## 4.3 Content Item

Use a product-agnostic identifier:

```json
{
  "product": "confluence",
  "contentType": "page",
  "tenantId": "tenant_123",
  "contentId": "page_456"
}
```

Future examples:

```text
product = jira, contentType = issue
product = bitbucket, contentType = pull_request
```

## 4.4 Engagement Event

Normalized event emitted by any product:

```json
{
  "eventId": "evt_123",
  "tenantId": "tenant_123",
  "product": "confluence",
  "contentType": "page",
  "contentId": "page_456",
  "actorId": "user_789",
  "eventType": "VIEW",
  "timestamp": "2026-09-25T10:00:00Z",
  "spaceId": "space_123",
  "ownerTeamId": "team_456",
  "metadata": {
    "device": "web",
    "source": "dashboard"
  }
}
```

---

## 5. Popularity Scoring

A simple weighted scoring model:

```text
score =
  view_count      * 1
+ reaction_count  * 5
+ comment_count   * 8
+ share_count     * 10
+ bookmark_count  * 6
```

Example:

```text
100 views * 1 = 100
10 likes * 5 = 50
4 comments * 8 = 32
2 shares * 10 = 20

total score = 202
```

## 5.1 Time-decay scoring

For trending content, recency should matter.

Example:

```text
decayed_score = raw_score * e^(-lambda * age)
```

For fixed windows like last 15 minutes or last 24 hours, use bucketed counters:

```text
15m window = sum last 15 one-minute buckets
24h window = sum last 24 one-hour buckets
7d window = sum last 7 daily buckets or 168 hourly buckets
```

Recommended:

> Use weighted event counts per time bucket, then aggregate buckets for requested windows.

---

## 6. High-Level Architecture

```text
Confluence / Jira / Bitbucket / Other Products
        |
        v
Engagement Event Producers
        |
        v
Event Gateway / Collector
        |
        v
Kafka / Event Bus
        |
        +-----------------------------+
        |                             |
        v                             v
Stream Aggregation Service       Raw Event Storage
        |                             |
        v                             v
Windowed Counter Store          Data Lake / Warehouse
        |
        v
Top-K Computation Service
        |
        v
Materialized Feed Store / Redis
        |
        v
Feed API Service
        |
        v
Permission Filter + Content Hydration
        |
        v
User Dashboard / Space Home
```

Batch reconciliation:

```text
Raw Event Storage / Data Lake
        |
        v
Batch Aggregation Jobs
        |
        v
Corrected Counters / Backfilled Top-K
```

---

## 7. Main Components

## 7.1 Event Producers

Each Atlassian product emits engagement events.

Examples:

- Confluence emits page view, reaction, comment, share events.
- Jira emits issue view, comment, transition, vote events.
- Bitbucket emits PR view, comment, approval, merge events.

Producer requirements:

- Include tenant ID.
- Include product key.
- Include content type.
- Include content ID.
- Include event type.
- Include timestamp.
- Include scope metadata where possible.

## 7.2 Event Gateway / Collector

Responsibilities:

- Validate event schema.
- Authenticate product producers.
- Enforce tenant isolation.
- Deduplicate events where possible.
- Add ingestion timestamp.
- Publish to Kafka.

Required fields:

```text
tenantId
product
contentType
contentId
eventType
eventId
timestamp
```

## 7.3 Kafka / Event Bus

Kafka topic:

```text
engagement-events
```

Partitioning options:

| Partition Key | Pros | Cons |
|---|---|---|
| `tenantId + contentId` | Good per-content ordering | Scope aggregation requires distributed combine |
| `tenantId + scopeId` | Easier scope aggregation | Hot spaces/sites can create hot partitions |
| `tenantId + hash(contentId)` | Better distribution | Requires aggregation fan-in |

Recommended:

> Partition by `tenantId + hash(contentId)` for load distribution, then aggregate by emitted scope keys in stream processors.

## 7.4 Stream Aggregation Service

Consumes engagement events and updates counters.

Responsibilities:

1. Normalize event.
2. Map event to score weight.
3. Expand event into relevant scopes.
4. Update time-bucketed counters.
5. Update approximate or exact top K for affected scopes/windows.
6. Emit aggregation metrics.

Scope expansion example for a Confluence page event:

```text
tenant:{tenantId}:site
tenant:{tenantId}:space:{spaceId}
tenant:{tenantId}:team:{ownerTeamId}
tenant:{tenantId}:product:confluence
```

One event may update multiple scope counters.

## 7.5 Windowed Counter Store

Stores per-content score by scope and bucket.

Example:

```text
scope = tenant_123:space:space_456
bucket = 2026-09-25T10:15:00Z
content = confluence:page:page_789
score = 37
views = 12
likes = 2
comments = 1
```

Possible stores:

- Cassandra/DynamoDB for high-write counters.
- Redis for hot counters.
- ClickHouse/Druid/Pinot for analytical aggregation.
- RocksDB state store inside Flink/Kafka Streams.

Recommended:

> Use stream processor local state/RocksDB for hot window aggregation, write materialized top K to Redis/DynamoDB, and persist raw events to a data lake for replay/backfill.

## 7.6 Top-K Computation Service

Computes top K for each scope/window.

---

## 8. Top-K Computation Approaches

## 8.1 Exact Top-K with sorted sets

Use Redis sorted sets:

```text
ZINCRBY popular:{scope}:{bucket} score contentRef
```

For a window:

```text
union last N bucket sorted sets
ZRANGE top K
```

Pros:

- Simple.
- Fast for smaller scopes.
- Easy to query.

Cons:

- Expensive for many scopes/windows.
- Redis memory-heavy.
- Large site-wide scopes can be hot.

## 8.2 Streaming top K per scope/window

Stream processor maintains a min-heap of size `K` per scope/window.

Pros:

- Efficient serving.
- Feed is precomputed.
- Good read latency.

Cons:

- More complex.
- Sliding windows require bucket expiration or recomputation.

## 8.3 Approximate heavy hitters

Use algorithms like:

- Count-Min Sketch.
- Space-Saving.
- Top-K heap per partition with merge.

Pros:

- Memory efficient.
- Good for huge streams.

Cons:

- Approximate.
- Need correction/backfill for accuracy.

Recommended hybrid:

> Maintain time-bucketed counters and periodically/materially compute Top-K per scope/window. Use streaming updates for near-real-time freshness and batch jobs for correction.

---

## 9. Time Window Strategy

Maintain fixed buckets instead of arbitrary sliding windows.

Examples:

```text
1-minute buckets for last 1 hour
1-hour buckets for last 7 days
1-day buckets for long-term analytics
```

Window calculation:

```text
last 15m = sum last 15 one-minute buckets
last 24h = sum last 24 one-hour buckets
last 7d = sum last 168 one-hour buckets or last 7 daily buckets
```

Frequently used windows should be precomputed:

```text
15m
1h
24h
7d
```

Materialized feed key:

```text
popular:{tenantId}:{scopeType}:{scopeId}:{window}:topK
```

Value:

```json
[
  {
    "contentRef": "confluence:page:page_123",
    "score": 982,
    "rank": 1
  },
  {
    "contentRef": "confluence:blog:blog_456",
    "score": 870,
    "rank": 2
  }
]
```

---

## 10. Feed API Design

## 10.1 Get Popular-K Feed

```http
GET /v1/popular
```

Query parameters:

```text
tenantId=tenant_123
scopeType=space
scopeId=space_456
window=24h
limit=20
contentTypes=page,blog,comment
cursor=...
```

Example:

```http
GET /v1/popular?scopeType=space&scopeId=space_456&window=24h&limit=20
```

Response:

```json
{
  "scope": {
    "type": "space",
    "id": "space_456"
  },
  "window": "24h",
  "generatedAt": "2026-09-25T10:30:00Z",
  "freshnessSeconds": 30,
  "items": [
    {
      "rank": 1,
      "contentRef": {
        "product": "confluence",
        "contentType": "page",
        "contentId": "page_123"
      },
      "score": 982,
      "signals": {
        "views": 700,
        "reactions": 40,
        "comments": 8,
        "shares": 2
      },
      "metadata": {
        "title": "Engineering Handbook",
        "url": "/wiki/spaces/ENG/pages/page_123",
        "owner": "Platform Team"
      }
    }
  ]
}
```

---

## 11. Read Path

```text
Dashboard requests popular feed
  -> Feed API Service
  -> Read materialized top K from Redis/DynamoDB
  -> Over-fetch top N, e.g. K * 3
  -> Permission Service filters inaccessible items
  -> Content Metadata Service hydrates title/url/owner/snippet
  -> Return top K visible items
```

Why over-fetch?

If a user lacks permission to top-ranked items, we need enough additional candidates to still return `K` visible items.

Example:

```text
Requested K = 10
Fetch top 50
Permission-filter
Return first 10 visible
```

---

## 12. Permission Filtering

Permission filtering is critical for Confluence.

## 12.1 Option 1: Filter at read time

Flow:

```text
precomputed popular feed
  -> fetch top N
  -> check permissions for requesting user
  -> return visible top K
```

Pros:

- Feed computation remains simple.
- No per-user feed explosion.
- Works across products.

Cons:

- Extra latency.
- Requires over-fetching.
- Permission service must be fast.

Recommended for v1.

## 12.2 Option 2: Precompute permission-aware feeds

Pros:

- Fast reads.

Cons:

- Explodes combinatorially.
- Hard with complex permissions.
- Not practical for large systems.

Recommendation:

> Use scope-level feed materialization and read-time permission filtering with over-fetching and caching.

---

## 13. Content Hydration

The Popular-K service should not own all product-specific metadata.

Use a product-agnostic content metadata interface:

```text
Content Metadata Service
  -> Confluence Content Adapter
  -> Jira Content Adapter
  -> Bitbucket Content Adapter
```

Common metadata contract:

```json
{
  "contentRef": {
    "product": "confluence",
    "contentType": "page",
    "contentId": "page_123"
  },
  "title": "Engineering Handbook",
  "url": "/wiki/spaces/ENG/pages/page_123",
  "ownerId": "team_456",
  "thumbnailUrl": "...",
  "lastUpdatedAt": "2026-09-25T09:00:00Z"
}
```

Cache hydrated metadata:

```text
content_meta:{product}:{contentType}:{contentId}
```

TTL:

```text
5-30 minutes
```

Invalidate on content update events.

---

## 14. Product-Agnostic Extensibility

## 14.1 ContentRef

```json
{
  "tenantId": "tenant_123",
  "product": "jira",
  "contentType": "issue",
  "contentId": "ISSUE-123"
}
```

## 14.2 EngagementEvent

```json
{
  "eventId": "evt_123",
  "tenantId": "tenant_123",
  "product": "jira",
  "contentType": "issue",
  "contentId": "ISSUE-123",
  "eventType": "COMMENT",
  "timestamp": "2026-09-25T10:00:00Z",
  "scopeRefs": [
    {
      "scopeType": "project",
      "scopeId": "PROJ"
    },
    {
      "scopeType": "team",
      "scopeId": "team_123"
    }
  ]
}
```

## 14.3 Signal weights configuration

Store weights in config:

```json
{
  "confluence": {
    "page": {
      "VIEW": 1,
      "REACTION": 5,
      "COMMENT": 8,
      "SHARE": 10
    }
  },
  "jira": {
    "issue": {
      "VIEW": 1,
      "COMMENT": 6,
      "VOTE": 5,
      "STATUS_CHANGE": 3
    }
  }
}
```

This allows adding products/signals without redesigning the pipeline.

---

## 15. Write Path / Streaming Update Flow

```text
User views Confluence page
  -> Confluence emits PageViewed event
  -> Event Gateway validates schema
  -> Kafka engagement-events
  -> Stream Aggregator consumes event
  -> Event expanded into scopes:
       site-wide
       space
       owner team
       product
  -> Weighted score computed
  -> Bucket counters updated
  -> Top-K materialization updated
  -> Feed cache refreshed
```

---

## 16. Batch Reconciliation

Streaming systems can miss events, double count, or process late events.

Use batch reconciliation:

```text
Raw events in data lake
  -> hourly/daily batch job
  -> recompute counters for windows
  -> compare with streaming counters
  -> correct materialized top K
```

Why needed?

- Late events.
- Duplicate events.
- Stream processing bugs.
- Backfills.
- Weight changes.
- Product-specific event replay.

Batch jobs can run:

```text
every 15 minutes for short windows
hourly for 24h
daily for 7d
```

---

## 17. Deduplication and Idempotency

Events may be delivered more than once.

Use `eventId`.

Dedup strategy:

```text
dedup:{eventId} -> seen
```

At high scale:

- Dedup within stream processor state for recent events.
- Use TTL matching max replay window.
- Use idempotent counter updates where possible.
- Batch reconciliation fixes residual inaccuracies.

Recommendation:

> Use at-least-once processing plus eventId deduplication and periodic batch correction.

---

## 18. Handling Late Events

Event timestamp may be older than ingestion timestamp.

Use event-time windows with allowed lateness.

Example:

```text
allowed_lateness = 5 minutes for 15m window
allowed_lateness = 1 hour for 24h/7d windows
```

If event arrives within allowed lateness:

```text
update corresponding historical bucket
recompute affected materialized windows
```

If too late:

```text
send to late-events topic
handled by batch reconciliation
```

---

## 19. Caching Strategy

## 19.1 Materialized feed cache

Key:

```text
popular:{tenantId}:{scopeType}:{scopeId}:{window}:{contentTypesHash}
```

Value:

```json
{
  "generatedAt": "2026-09-25T10:30:00Z",
  "items": []
}
```

TTL:

```text
30-120 seconds
```

Feeds should mostly be updated by stream processors, not only TTL.

## 19.2 Permission result cache

Key:

```text
perm:{userId}:{contentRef}
```

TTL:

```text
1-5 minutes
```

Use short TTLs or invalidation because permission changes are security-sensitive.

## 19.3 Content metadata cache

Key:

```text
content_meta:{product}:{contentType}:{contentId}
```

TTL:

```text
5-30 minutes
```

Invalidate on content update events.

---

## 20. Indexing

The Popular-K service mostly reads from materialized top-K stores.

Indexing is still useful for:

1. Content metadata lookup.
2. Feed exploration/search.
3. Admin debugging.
4. Analytics.

Possible indexes:

```text
contentRef -> metadata
scope -> contentRefs
tenant -> active scopes
contentRef -> latest score
```

Search index fields:

```text
tenantId
product
contentType
contentId
scopeRefs
title
ownerTeamId
lastUpdatedAt
latestPopularityScore
```

OpenSearch can be useful for debugging/admin interfaces, but not required for the hot feed read path.

---

## 21. Storage Strategy

| Data | Store | Why |
|---|---|---|
| Raw engagement events | Kafka + object storage/data lake | Replay, audit, batch correction |
| Stream processor state | RocksDB/Flink state | Fast window aggregation |
| Materialized Top-K feed | Redis/DynamoDB/Cassandra | Low-latency feed reads |
| Bucketed counters | Cassandra/DynamoDB/ClickHouse | Aggregation and recomputation |
| Content metadata cache | Redis | Fast hydration |
| Feed config/weights | PostgreSQL/config service | Strong config management |
| Analytics | Data warehouse | Reporting and experimentation |

---

## 22. Scalability

## 22.1 Scale by tenant and scope

Feeds are naturally partitioned by:

```text
tenantId
scopeType
scopeId
window
```

## 22.2 Hot tenants/spaces

Large tenants or busy spaces can be hot.

Mitigations:

- Partition counters by content hash.
- Use local top K per partition, then merge.
- Maintain hierarchical top K:
  ```text
  partition top K -> scope top K
  ```
- Increase Redis replicas for hot keys.
- Cache heavily on dashboards.
- Use CDN/edge caching only if response is not user-specific or after permission filtering is safe.

## 22.3 Large number of scopes

Do not compute every possible scope/window eagerly if unused.

Use hybrid strategy:

- Always compute popular scopes:
  - site-wide.
  - active spaces.
  - active teams.
- Lazy compute rarely used scopes.
- Evict inactive scope feeds.

---

## 23. Top-K Algorithm Details

## 23.1 Exact small-scope algorithm

For small/medium scopes:

```text
Update counter per content per bucket.
Maintain sorted set per scope/window.
Return ZREVRANGE top K.
```

## 23.2 Large-scope algorithm

For high-cardinality scopes:

```text
1. Partition events by hash(contentRef).
2. Each partition maintains local top K.
3. Periodically merge local top K into global top K.
4. Store global materialized feed.
```

## 23.3 Approximate algorithm

Use Space-Saving algorithm:

- Keep fixed-size candidate set.
- Track approximate counts.
- Merge sketches across partitions.

Good when:

- Very high event volume.
- Exact accuracy is not required.
- Feed can tolerate approximate ranking.

Recommendation:

> Use exact counters for normal scopes and approximate heavy-hitter algorithms for extremely high-volume site-wide feeds.

---

## 24. Consistency Model

| Area | Consistency | Reason |
|---|---|---|
| Event ingestion | At-least-once | Durable and scalable |
| Stream counters | Eventually consistent | Events can be late/replayed |
| Top-K feed | Eventually consistent | Near-real-time freshness enough |
| Permission filtering | Strong at request time | Must not leak restricted content |
| Metadata hydration | Eventual | Slightly stale title/owner acceptable |
| Batch reconciliation | Corrective eventual consistency | Fixes stream drift |
| Feed config | Strong | Weight changes should be controlled |

Important:

> Popularity score can be eventually consistent, but permission checks must be strongly enforced before returning content to a user.

---

## 25. Failure Scenarios

## 25.1 Kafka lag increases

Impact:

- Feed freshness degrades.

Handling:

- Continue serving last materialized feed.
- Include freshness timestamp.
- Alert on lag.
- Scale consumers.
- Shed non-critical scopes if needed.
- Batch reconciliation later corrects.

## 25.2 Stream processor crashes

Handling:

- Restart from Kafka offsets/checkpoints.
- Restore state from checkpoint.
- Reprocess events idempotently.
- Serve last materialized feed meanwhile.

## 25.3 Redis/materialized feed store unavailable

Handling:

- Fallback to DynamoDB/Cassandra feed store if available.
- Return stale cached response from local cache if safe.
- Degrade feed widget.
- Do not block core Confluence page loads.

## 25.4 Permission service slow/down

This is security-sensitive.

Handling:

- Do not return unfiltered content.
- Either return empty/degraded feed or only content already known visible.
- Use short-lived permission cache.
- Fail closed for restricted scopes.

## 25.5 Metadata service unavailable

Handling:

- Return feed with minimal contentRef if product UI can hydrate.
- Use cached metadata.
- Degrade thumbnails/titles if needed.

## 25.6 Batch job fails

Handling:

- Streaming feed continues.
- Alert.
- Retry batch job.
- Mark reconciliation lag.

---

## 26. API Freshness and Degraded Responses

Feed response should include freshness:

```json
{
  "generatedAt": "2026-09-25T10:30:00Z",
  "freshnessSeconds": 45,
  "isStale": false,
  "items": []
}
```

If stale:

```json
{
  "generatedAt": "2026-09-25T10:20:00Z",
  "freshnessSeconds": 645,
  "isStale": true,
  "degradedReason": "STREAM_PROCESSING_LAG"
}
```

This helps product UIs decide whether to show a stale badge or hide the widget.

---

## 27. Multi-Tenancy

Every key and event must include `tenantId`.

Examples:

```text
popular:{tenantId}:{scopeType}:{scopeId}:{window}
counter:{tenantId}:{scope}:{bucket}:{contentRef}
dedup:{tenantId}:{eventId}
```

Tenant isolation requirements:

- No cross-tenant feed leakage.
- Separate rate limits per tenant/product.
- Tenant-aware encryption.
- Tenant-aware access control.
- Optional data residency support.

---

## 28. Security and Privacy

- Authenticate event producers.
- Validate event schema.
- Enforce tenant isolation.
- Encrypt data in transit and at rest.
- Do not expose restricted content.
- Do not log sensitive content titles if restricted.
- Rate limit feed APIs.
- Audit admin/config changes.
- Respect data retention policies.
- Support deletion events for content/user data.

Deletion event handling:

```text
ContentDeleted
  -> remove from materialized feeds
  -> remove metadata cache
  -> mark content unavailable
```

---

## 29. Observability

## 29.1 Ingestion metrics

```text
events_ingested_per_second
events_by_product
events_by_type
event_validation_failures
dedup_rate
late_event_count
```

## 29.2 Stream processing metrics

```text
consumer_lag
processing_latency
checkpoint_latency
state_size
counter_update_rate
topk_update_rate
```

## 29.3 Feed serving metrics

```text
feed_qps
feed_latency_p50_p95_p99
feed_cache_hit_ratio
permission_filter_latency
content_hydration_latency
overfetch_ratio
empty_feed_rate
stale_feed_rate
```

## 29.4 Ranking metrics

```text
topk_score_distribution
rank_churn_rate
click_through_rate
dismiss_rate
engagement_after_impression
```

## 29.5 Alerts

Alert on:

- Kafka lag beyond freshness SLA.
- Stream processor crash/restart loop.
- Feed API latency spike.
- Permission service failures.
- Empty feed rate spike.
- Materialized feed store errors.
- Batch reconciliation lag.
- Cross-tenant access anomaly.
- Hot key or hot scope overload.

---

## 30. Zero-Downtime Deployment

Use:

- Backward-compatible event schemas.
- Schema registry.
- Consumer versioning.
- Dual-read/dual-write for new stores.
- Canary stream processors.
- Feature flags for scoring changes.
- Config versioning for signal weights.
- Replay capability from raw event store.

For scoring changes:

```text
1. Add new scoring config version.
2. Run shadow computation.
3. Compare old vs new feed quality.
4. Gradually roll out.
5. Backfill materialized feeds if needed.
```

---

## 31. Final Architecture Summary

```text
Products
Confluence / Jira / Bitbucket
        |
        v
Engagement Event Gateway
        |
        v
Kafka engagement-events
        |
        +-----------------------------+
        |                             |
        v                             v
Stream Aggregation Service       Raw Event Storage
        |                             |
        v                             v
Windowed Counters              Data Lake
        |                             |
        v                             v
Top-K Materializer             Batch Reconciliation
        |                             |
        v                             |
Redis / DynamoDB Feed Store <--------+
        |
        v
Feed API
        |
        v
Permission Filtering
        |
        v
Content Metadata Hydration
        |
        v
Dashboard / Space Home Feed
```

---

## 32. Senior-Level Closing Statement

A strong interview summary:

> I would design Popular-K as a product-agnostic streaming aggregation platform. All Atlassian products emit normalized engagement events with tenant, content reference, event type, timestamp, and scope references. Kafka provides durable ingestion. Stream processors expand each event into relevant scopes, update time-bucketed weighted counters, and materialize top-K feeds for common windows like 15 minutes, 24 hours, and 7 days. Feed reads are served from a low-latency materialized store like Redis or DynamoDB, then permission-filtered and hydrated with product metadata before returning to the user. Popularity can be eventually consistent, but permission checks must be strongly enforced at read time. Batch reconciliation from raw events corrects late, duplicate, or missed events. The system remains extensible by using product-agnostic `ContentRef`, normalized `EngagementEvent`, and configurable signal weights.
