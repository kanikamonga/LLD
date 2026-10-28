# High-Level Design: Product-Agnostic Tagging System for Atlassian Content

## Original Problem Statement

Atlassian has multiple products with different content types:

```text
Jira        -> Issues
Confluence  -> Pages
Bitbucket   -> Pull Requests
```

We want to build a system that allows users to tag content from different products and then view content by tags.

The system should be product-agnostic so that new Atlassian products can be added later without a large redesign.

There are three key experiences:

1. As a user, I can add, remove, and update tags on content.
2. As a user, I can click on a tag and see all content associated with that tag.
3. As a user, I can see a dashboard of popular tags.

---

## 1. Key Design Principle

Use a common `ContentRef` abstraction.

```json
{
  "tenantId": "tenant_123",
  "product": "jira",
  "contentType": "issue",
  "contentId": "ISSUE-123"
}
```

Examples:

```json
{
  "tenantId": "tenant_123",
  "product": "confluence",
  "contentType": "page",
  "contentId": "page_456"
}
```

```json
{
  "tenantId": "tenant_123",
  "product": "bitbucket",
  "contentType": "pull_request",
  "contentId": "repo_1/pr_789"
}
```

The tagging system stores mappings between:

```text
Tag <-> ContentRef
```

It does not need to know how Jira stores issues, how Confluence stores pages, or how Bitbucket stores pull requests.

---

## 2. Requirements

### Functional requirements

#### Tag management

Users can:

- Add tag to content.
- Remove tag from content.
- Rename/update tag.
- List tags for content.
- Optionally create tags explicitly.

#### Browse by tag

Users can:

- Click a tag.
- View all content associated with that tag.
- Filter by product/content type.
- Sort by recently tagged, recently updated, popularity, etc.

#### Popular tags dashboard

Users can view popular tags by:

- Tenant/site.
- Product.
- Space/project/repository/team scope.
- Time window:
  - Last 1 hour.
  - Last 24 hours.
  - Last 7 days.
  - All time.

#### Product extensibility

New products should integrate by:

- Emitting content metadata.
- Implementing permission checks.
- Providing content hydration APIs.

The tagging core should not need schema redesign for each product.

### Non-functional requirements

| Requirement | Target |
|---|---:|
| Availability | 99.99% |
| Tag write latency | P95 < 200 ms |
| Tag read latency | P95 < 100-200 ms |
| Browse-by-tag latency | P95 < 300 ms |
| Consistency for tag writes | Strong for direct tag changes |
| Search/index freshness | Eventual, seconds to minutes |
| Multi-tenancy | Strong tenant isolation |
| Permission correctness | Strong at read time |
| Scalability | Millions of content items, tags, and associations |
| Extensibility | Product-agnostic content model |

---

## 3. Important Clarifications

### Tags are tenant-scoped

The same tag text can exist in different Atlassian tenants:

```text
tenant_A: frontend
tenant_B: frontend
```

These are separate logical tags.

### Tag names need normalization

Normalize tags for uniqueness/search:

```text
"Frontend"
"frontend"
" front-end "
```

May normalize to:

```text
frontend
```

But display name can preserve casing:

```text
displayName = "Frontend"
normalizedName = "frontend"
```

### Permissions must be enforced

If a user clicks a tag, they should only see content they are allowed to access.

The tagging index may contain restricted content. Therefore, the read path must call product permission services or use safe cached permission decisions.

Important rule:

> Tag association can be eventually indexed, but permission filtering must be enforced before returning content.

---

## 4. High-Level Architecture

```text
Jira / Confluence / Bitbucket / Future Products
        |
        v
Product Content APIs
        |
        v
+-------------------+
| Tagging API       |
| - add tag         |
| - remove tag      |
| - list tags       |
| - browse by tag   |
+---------+---------+
          |
          v
+-------------------+
| Tagging Service   |
| - validates tags  |
| - stores mappings |
| - emits events    |
+----+----------+---+
     |          |
     v          v
Tag DB       Kafka/Event Bus
     |          |
     |          +-----------------------------+
     |                                        |
     v                                        v
Tag Index / Search Store              Popular Tag Aggregator
     |                                        |
     v                                        v
Browse-by-tag API                 Popular Tags Store / Redis
     |
     v
Permission Filter + Content Hydration
     |
     v
User UI
```

---

## 5. Core Data Model

## 5.1 Tags table

```text
tags(
  tag_id PK,
  tenant_id,
  normalized_name,
  display_name,
  description,
  created_by,
  created_at,
  updated_at,
  status,
  UNIQUE(tenant_id, normalized_name)
)
```

Example:

```text
tag_id = tag_123
tenant_id = tenant_1
normalized_name = frontend
display_name = Frontend
```

## 5.2 Content tag association

```text
content_tags(
  tenant_id,
  tag_id,
  product,
  content_type,
  content_id,
  tagged_by,
  tagged_at,
  updated_at,
  status,
  PRIMARY KEY(tenant_id, tag_id, product, content_type, content_id)
)
```

This supports:

```text
tag -> content
```

## 5.3 Content-to-tag lookup

Need efficient lookup for:

```text
content -> tags
```

Maintain another table/index:

```text
content_tag_lookup(
  tenant_id,
  product,
  content_type,
  content_id,
  tag_id,
  tagged_by,
  tagged_at,
  PRIMARY KEY(tenant_id, product, content_type, content_id, tag_id)
)
```

This supports:

```text
GET /content/{contentRef}/tags
```

## 5.4 Tag usage counters

```text
tag_stats(
  tenant_id,
  tag_id,
  product,
  content_type,
  usage_count,
  last_used_at,
  updated_at,
  PRIMARY KEY(tenant_id, tag_id, product, content_type)
)
```

For popular tags, additionally maintain time-windowed counters:

```text
tag_popularity_buckets(
  tenant_id,
  scope_type,
  scope_id,
  bucket_start,
  tag_id,
  event_count,
  unique_content_count,
  unique_user_count
)
```

---

## 6. ContentRef Model

A product-agnostic content reference:

```json
{
  "tenantId": "tenant_123",
  "product": "confluence",
  "contentType": "page",
  "contentId": "page_456"
}
```

Supported examples:

| Product | Content type | Content ID |
|---|---|---|
| Jira | issue | `ISSUE-123` |
| Confluence | page | `page_456` |
| Bitbucket | pull_request | `workspace/repo/pr_789` |

Future products only need to provide:

1. Stable product key.
2. Stable content type.
3. Stable content ID.
4. Permission API.
5. Metadata/hydration API.

---

## 7. API Design

## 7.1 Add tag to content

```http
POST /v1/tags/associations
```

Request:

```json
{
  "contentRef": {
    "tenantId": "tenant_123",
    "product": "jira",
    "contentType": "issue",
    "contentId": "ISSUE-123"
  },
  "tag": {
    "name": "Frontend"
  }
}
```

Response:

```json
{
  "tagId": "tag_123",
  "displayName": "Frontend",
  "normalizedName": "frontend",
  "contentRef": {
    "product": "jira",
    "contentType": "issue",
    "contentId": "ISSUE-123"
  },
  "taggedAt": "2026-09-27T10:00:00Z"
}
```

Behavior:

1. Normalize tag.
2. Create tag if missing.
3. Check user permission to tag content.
4. Insert association idempotently.
5. Emit `TagAdded` event.

## 7.2 Remove tag from content

```http
DELETE /v1/tags/associations
```

Request:

```json
{
  "contentRef": {
    "tenantId": "tenant_123",
    "product": "jira",
    "contentType": "issue",
    "contentId": "ISSUE-123"
  },
  "tagId": "tag_123"
}
```

Response:

```json
{
  "removed": true
}
```

Behavior:

- Check user permission.
- Soft delete or delete association.
- Emit `TagRemoved` event.

## 7.3 Update/rename tag

```http
PATCH /v1/tags/{tagId}
```

Request:

```json
{
  "displayName": "Frontend Platform"
}
```

Behavior:

- Only allowed for authorized users.
- Updates tag metadata.
- Does not need to rewrite associations because associations use `tagId`.

## 7.4 List tags for content

```http
GET /v1/content/tags?product=jira&contentType=issue&contentId=ISSUE-123
```

Response:

```json
{
  "contentRef": {
    "product": "jira",
    "contentType": "issue",
    "contentId": "ISSUE-123"
  },
  "tags": [
    {
      "tagId": "tag_123",
      "displayName": "Frontend",
      "normalizedName": "frontend"
    }
  ]
}
```

## 7.5 View content by tag

```http
GET /v1/tags/{tagId}/content
```

Query parameters:

```text
product=jira
contentType=issue
limit=20
cursor=...
sort=recently_tagged
```

Response:

```json
{
  "tag": {
    "tagId": "tag_123",
    "displayName": "Frontend"
  },
  "items": [
    {
      "contentRef": {
        "product": "jira",
        "contentType": "issue",
        "contentId": "ISSUE-123"
      },
      "metadata": {
        "title": "Fix frontend build issue",
        "url": "https://example.atlassian.net/browse/ISSUE-123",
        "owner": "Platform Team"
      },
      "taggedAt": "2026-09-27T10:00:00Z"
    }
  ],
  "nextCursor": "opaque_cursor"
}
```

Read path:

```text
Tag Index -> over-fetch candidates -> permission filter -> hydrate metadata -> return visible items
```

## 7.6 Popular tags dashboard

```http
GET /v1/tags/popular
```

Query parameters:

```text
tenantId=tenant_123
scopeType=site
scopeId=site
window=24h
limit=20
product=confluence
```

Response:

```json
{
  "scope": {
    "type": "site",
    "id": "site"
  },
  "window": "24h",
  "items": [
    {
      "tagId": "tag_123",
      "displayName": "Frontend",
      "score": 982,
      "contentCount": 340,
      "recentUsageCount": 50
    }
  ],
  "generatedAt": "2026-09-27T10:00:00Z"
}
```

---

## 8. Write Flow: Add Tag

```text
User adds tag "Frontend" to Jira issue ISSUE-123
  -> Tagging API
  -> AuthN/AuthZ
  -> Normalize tag name
  -> Check user can edit/tag this issue
  -> Upsert tag row
  -> Insert content_tags association
  -> Insert content_tag_lookup association
  -> Emit TagAdded event
  -> Return success
```

Transaction:

```text
BEGIN
  upsert tags
  insert content_tags
  insert content_tag_lookup
  insert outbox_event(TagAdded)
COMMIT
```

Use outbox pattern so DB write and event emission stay consistent.

---

## 9. Read Flow: View Content by Tag

```text
User clicks tag
  -> GET /tags/{tagId}/content
  -> Query tag index/content_tags for candidate contentRefs
  -> Over-fetch, e.g. limit * 3 or limit * 5
  -> Permission check candidates
  -> Hydrate visible items from product metadata APIs
  -> Return first K visible results
```

Why over-fetch?

Some tagged content may be restricted. If user asks for 20 items, we may fetch 100 candidates, permission-filter them, and return the first 20 visible.

---

## 10. Permission Filtering

Permission handling is the most important correctness concern.

## 10.1 Option 1: Read-time permission filtering

Recommended for v1.

```text
candidate contentRefs
  -> Product Permission Service
  -> filter inaccessible content
  -> hydrate visible content
```

Pros:

- Product-agnostic.
- Does not explode storage.
- Handles dynamic permission changes.

Cons:

- Adds latency.
- Requires over-fetching.
- Permission service must be reliable and fast.

## 10.2 Option 2: Precompute permission-aware tag views

Not recommended initially.

Cons:

- Too many users/groups/permissions combinations.
- Complex with Confluence/Jira permission models.
- Hard to keep updated.

Recommendation:

> Use product-level permission checks at read time. Fail closed if permission service is unavailable for restricted content.

---

## 11. Content Hydration

Tagging service should not own Jira issue titles or Confluence page URLs.

Use a product-agnostic metadata interface.

```text
Content Metadata Service
  -> Jira Adapter
  -> Confluence Adapter
  -> Bitbucket Adapter
```

Common contract:

```json
{
  "contentRef": {
    "product": "jira",
    "contentType": "issue",
    "contentId": "ISSUE-123"
  },
  "title": "Fix frontend build issue",
  "url": "https://example.atlassian.net/browse/ISSUE-123",
  "ownerId": "team_123",
  "lastUpdatedAt": "2026-09-27T09:00:00Z",
  "thumbnailUrl": null
}
```

Cache hydrated metadata:

```text
content_meta:{tenantId}:{product}:{contentType}:{contentId}
```

TTL:

```text
5-30 minutes
```

Invalidate on product content update events.

---

## 12. Popular Tags Design

Popular tags can be based on:

- Number of content items using the tag.
- Number of new tag associations.
- Number of user interactions with tagged content.
- Recent usage.
- Unique users applying/clicking tag.

Simple score:

```text
score =
  content_count * 1
+ recent_add_count * 3
+ click_count * 2
+ unique_user_count * 5
```

For v1, use:

```text
usage_count = number of active content associations
```

For near-real-time dashboard, use event aggregation.

## 12.1 Tag events

Emit events:

```text
TagCreated
TagAddedToContent
TagRemovedFromContent
TagClicked
TagRenamed
```

Example:

```json
{
  "eventId": "evt_123",
  "tenantId": "tenant_123",
  "eventType": "TagAddedToContent",
  "tagId": "tag_123",
  "contentRef": {
    "product": "confluence",
    "contentType": "page",
    "contentId": "page_456"
  },
  "actorId": "user_789",
  "timestamp": "2026-09-27T10:00:00Z"
}
```

## 12.2 Popular tag aggregation

Architecture:

```text
Tagging Service
  -> Tag events
  -> Kafka
  -> Popular Tag Aggregator
  -> Windowed counters
  -> Redis/DynamoDB popular tag materialization
  -> Popular Tags API
```

Materialized key:

```text
popular_tags:{tenantId}:{scopeType}:{scopeId}:{window}
```

Value:

```json
[
  {
    "tagId": "tag_123",
    "score": 982,
    "rank": 1
  }
]
```

---

## 13. Indexing Strategy

We need efficient access patterns.

## 13.1 Tag to content

```text
tenantId + tagId -> contentRefs
```

Used by:

```text
GET /tags/{tagId}/content
```

Store/index:

```text
content_tags_by_tag(
  tenant_id,
  tag_id,
  tagged_at,
  product,
  content_type,
  content_id
)
```

Partition key:

```text
tenant_id + tag_id
```

Sort key:

```text
tagged_at DESC
```

## 13.2 Content to tags

```text
tenantId + contentRef -> tags
```

Used by:

```text
GET /content/tags
```

Store/index:

```text
tags_by_content(
  tenant_id,
  product,
  content_type,
  content_id,
  tag_id
)
```

## 13.3 Popular tags

```text
tenantId + scope + window -> top tags
```

Store:

```text
popular_tags:{tenantId}:{scopeType}:{scopeId}:{window}
```

---

## 14. Storage Choices

| Data | Suggested store | Why |
|---|---|---|
| Tag metadata | PostgreSQL / DynamoDB | Strong tag identity and uniqueness |
| Tag-content associations | DynamoDB/Cassandra/PostgreSQL partitioned | High read/write lookup by tag/content |
| Tag search index | OpenSearch | Prefix/search/autocomplete tags |
| Popular tag counters | Redis/DynamoDB/Flink state | Fast dashboard reads |
| Tag events | Kafka + data lake | Replay and analytics |
| Metadata cache | Redis | Fast content hydration |
| Permission cache | Redis short TTL | Reduce permission-service calls |

For Atlassian-scale multi-tenant systems:

- DynamoDB/Cassandra-style wide-column storage works well for associations.
- PostgreSQL works for smaller scale or per-tenant partitioning.
- OpenSearch is useful for tag search/autocomplete, not necessarily source of truth.

---

## 15. Multi-Tenancy

Every table/key must include:

```text
tenantId
```

Examples:

```text
tag:{tenantId}:{tagId}
tag_name:{tenantId}:{normalizedName}
content_tags:{tenantId}:{tagId}
tags_by_content:{tenantId}:{product}:{contentType}:{contentId}
popular_tags:{tenantId}:{scope}:{window}
```

Tenant isolation requirements:

- No cross-tenant tag leakage.
- Tenant-aware encryption.
- Tenant-level rate limits.
- Tenant-specific data residency if required.
- Tenant-aware deletion/retention.

---

## 16. Product-Agnostic Extensibility

To add a new product, require the product to implement:

## 16.1 ContentRef registration

```json
{
  "product": "trello",
  "contentTypes": ["card", "board"]
}
```

## 16.2 Permission adapter

```text
canView(userId, contentRef)
canTag(userId, contentRef)
```

## 16.3 Metadata adapter

```text
hydrate(contentRefs) -> metadata[]
```

## 16.4 Content lifecycle events

Products should emit:

```text
ContentCreated
ContentUpdated
ContentDeleted
PermissionChanged
```

Tagging system consumes these to:

- Remove deleted content from tag indexes.
- Invalidate metadata cache.
- Handle permission changes.

---

## 17. Consistency Model

| Operation | Consistency | Reason |
|---|---|---|
| Add/remove tag | Strong in tag DB | User expects immediate confirmation |
| List tags for content | Strong or read-your-write | User should see recently added tag |
| Browse content by tag | Eventual acceptable if index async | Slight delay acceptable |
| Popular tags | Eventual | Dashboard can lag |
| Content hydration | Eventual | Title/URL can be slightly stale |
| Permission filtering | Strong at read time | Must not leak restricted content |
| Tag rename | Strong for tag metadata, eventual in caches | Rename should converge |
| Deleted content removal | Eventually removed from index, hidden by hydration/permission checks | Must not show inaccessible/deleted content |

---

## 18. Handling Content Deletion

When a product deletes content:

```text
Product emits ContentDeleted event
  -> Tagging Service marks associations inactive
  -> Remove from tag index
  -> Invalidate metadata cache
  -> Update tag stats/popularity
```

Read-time protection:

- If deleted content still appears in tag index temporarily, hydration should fail or return deleted.
- API should filter it out.

---

## 19. Failure Scenarios

## 19.1 Tag DB unavailable

Impact:

- Cannot add/remove tags.
- Reads may degrade depending on cache/index.

Handling:

- Return write failure.
- Do not fake success.
- Read from cache/index if safe.
- Alert immediately.

## 19.2 Kafka/event bus lag

Impact:

- Popular tags and secondary indexes may lag.

Handling:

- Direct tag DB remains source of truth.
- Show stale popular tag dashboard with freshness timestamp.
- Scale consumers.
- Replay events if needed.

## 19.3 Permission service unavailable

Security-sensitive.

Handling:

- Fail closed for restricted content.
- Return fewer results or degraded response.
- Use short-lived permission cache only if safe.
- Do not show unverified content.

## 19.4 Metadata service unavailable

Handling:

- Return content IDs only if product UI can hydrate.
- Or hide items lacking metadata.
- Use metadata cache.
- Degrade thumbnails/titles.

## 19.5 Popular tag aggregator down

Impact:

- Popular tags dashboard stale.

Handling:

- Serve last materialized popular tags with `generatedAt`.
- Alert on freshness lag.
- Recompute from event log.

---

## 20. Caching Strategy

## 20.1 Tag metadata cache

```text
tag:{tenantId}:{tagId}
```

TTL:

```text
5-30 minutes
```

## 20.2 Content tags cache

```text
tags_by_content:{tenantId}:{product}:{contentType}:{contentId}
```

TTL:

```text
1-5 minutes
```

Invalidate on tag add/remove.

## 20.3 Tag content cache

```text
content_by_tag:{tenantId}:{tagId}:{filtersHash}:{page}
```

TTL:

```text
30-120 seconds
```

Must still apply permission filtering.

## 20.4 Permission cache

```text
perm:{tenantId}:{userId}:{contentRef}
```

TTL:

```text
30 seconds - 5 minutes
```

Use carefully because permission changes are security-sensitive.

## 20.5 Popular tags cache

```text
popular_tags:{tenantId}:{scope}:{window}
```

Updated by aggregator and has TTL fallback.

---

## 21. Observability

Metrics:

```text
tag_add_qps
tag_remove_qps
tag_read_qps
content_by_tag_qps
popular_tags_qps
tag_write_latency_p95
tag_read_latency_p95
permission_filter_latency
metadata_hydration_latency
tag_index_lag
popular_tag_freshness_lag
event_consumer_lag
empty_tag_result_rate
permission_denied_count
```

Alerts:

- Tag write latency spike.
- Tag DB errors.
- Permission service failures.
- Event consumer lag.
- Popular tags freshness lag.
- Metadata hydration failure spike.
- Cross-tenant access anomaly.
- High deleted-content result rate.

Logs should include:

```text
tenantId
userId
tagId
contentRef
operation
requestId
errorCode
```

Do not log restricted content titles unnecessarily.

---

## 22. Security and Privacy

- Authenticate all API calls.
- Authorize user before tagging content.
- Permission-filter results before returning content.
- Encrypt data in transit and at rest.
- Enforce tenant isolation.
- Audit tag changes.
- Rate-limit tag write APIs.
- Prevent abusive tag spam.
- Validate tag names and length.
- Support content deletion and user data retention policies.

Tag validation:

```text
max length
allowed characters
blocked offensive/system-reserved names
case normalization
```

---

## 23. Scaling Considerations

## 23.1 High-cardinality tags

Some tags may be very popular:

```text
incident
frontend
security
```

Large result sets require:

- Pagination.
- Cursor-based reads.
- Partitioned tag-content indexes.
- Over-fetch + permission filtering.
- Possibly precomputed pages for hot tags.

## 23.2 Hot tenants

Large tenants may generate many tag writes.

Mitigation:

- Partition by `tenantId + tagId`.
- Use rate limits.
- Use async indexing.
- Cache popular reads.

## 23.3 Popular tags dashboard

Use precomputed materialized views, not live aggregation on every request.

---

## 24. Final Architecture Summary

```text
User / Product UI
        |
        v
Tagging API
        |
        v
Tagging Service
        |
        +--------------------------+
        |                          |
        v                          v
Tag DB / Association Store      Outbox Events
        |                          |
        v                          v
Tag Index                    Kafka/Event Bus
        |                          |
        v                          v
Browse-by-tag API          Popular Tag Aggregator
        |                          |
        v                          v
Permission Filter          Popular Tags Store
        |
        v
Content Metadata Hydration
        |
        v
Visible Tagged Content
```

---

## 25. Senior-Level Closing Statement

A strong interview answer:

> I would design tagging around a product-agnostic `ContentRef` containing tenant, product, content type, and content ID. The core Tagging Service stores tag metadata and tag-content associations without depending on Jira, Confluence, or Bitbucket internals. It supports both tag-to-content and content-to-tag lookup patterns. Writes are strongly persisted and emit events through an outbox to update secondary indexes, popular tag aggregates, and caches asynchronously. When users click a tag, the service fetches candidate content references, over-fetches to account for permissions, checks visibility through product permission adapters, hydrates product-specific metadata through a common metadata interface, and returns only visible content. Popular tags are computed asynchronously from tag events using windowed counters and served from a materialized store. This keeps the system extensible for new products while preserving tenant isolation and permission correctness.
