# Redis Data Structures — Staff Backend Engineer Interview Notes

## 1. Staff-level summary

Redis provides in-memory data structures with operations tailored to common access patterns. Select a structure based on the operations the application needs, then account for memory, expiration, atomicity, persistence, and failure behavior.

## 2. Core data structures

| Structure | What it stores | Common use cases | Main benefit |
|---|---|---|---|
| **Strings** | A byte sequence; can also represent numbers | Cache values, counters, tokens, feature flags | Simple reads/writes; supports atomic operations such as `INCR` |
| **Hashes** | Field-value pairs under one key | User profiles, session attributes, compact records | Update or retrieve individual fields without replacing the whole record |
| **Lists** | Ordered sequence of values | Recent activity, simple queues, bounded histories | Efficient push/pop operations at either end |
| **Sets** | Unique, unordered members | Membership checks, tags, deduplication | Uniqueness and set operations such as intersection and union |
| **Sorted Sets** | Unique members associated with numeric scores | Leaderboards, rankings, priority or time indexes | Efficient score ordering and range queries |
| **Streams** | Ordered entries with IDs | Event processing, replayable work, consumer groups | Tracks entries and consumer progress, including pending work |

Redis also offers specialized structures:

- **Bitmaps:** Compact boolean flags, such as whether a user was active on a given day.
- **HyperLogLog:** Memory-efficient *approximate* distinct counts, such as daily unique visitors.
- **Geospatial indexes:** Location and radius queries, such as finding nearby stores or drivers. These use Redis's sorted-set capabilities.
- **Module-provided types:** Depending on the Redis distribution and enabled modules, options may include JSON or Bloom filters.

## 3. Examples and use cases

### Strings — cache or counter

```text
SET product:42 '{"name":"Keyboard","price":80}' EX 300
INCR page:home:views
```

Strings work well for straightforward key-to-value access and atomic single-command updates. A sequence of commands is not automatically atomic.

### Hashes — entity fields

```text
HSET user:42 name "Sam" status "active"
HGET user:42 status
```

Hashes are useful when the application often reads or updates individual fields. A Redis hash is not a full replacement for a relational database: it does not provide relational constraints or arbitrary SQL queries.

### Lists — ordered items

```text
LPUSH recent:users:42 "product:7"
LTRIM recent:users:42 0 19
```

Lists work for a recent-items list or a simple queue. For durable work with consumer coordination, retries, or replay, consider **Streams** rather than treating a List as a complete job-processing system.

### Sets — uniqueness and membership

```text
SADD user:42:interests "music" "travel"
SISMEMBER user:42:interests "music"
```

Sets are useful for membership checks, deduplication, and set operations. They do not preserve order.

### Sorted Sets — ranking

```text
ZADD leaderboard 1200 "player:42"
ZREVRANGE leaderboard 0 9 WITHSCORES
```

Sorted Sets are useful for leaderboards and score-based retrieval. They can also index values by priority or time, though a scheduler built on them still needs safe claiming, retries, and recovery.

### Streams — event-like processing

Streams support ordered entries and consumer groups, making them useful when consumers need to track progress and coordinate work. Define retention and pending-entry recovery policies; a Stream should not automatically be treated as an unlimited event archive.

## 4. Choosing a structure

| Need | Suitable structure |
|---|---|
| One cached value or atomic counter | String |
| Named fields for one entity | Hash |
| Ordered items with pushes and pops | List |
| Uniqueness, membership, or set algebra | Set |
| Ranking or score-based ranges | Sorted Set |
| Event-like log with consumer groups | Stream |
| Compact boolean flags | Bitmap |
| Approximate distinct count | HyperLogLog |
| Nearby-location queries | Geospatial index |

The best fit depends on the operations and access patterns required—not only on the shape of the domain data.

## 5. Production considerations

- **Memory:** Define TTLs and eviction behavior intentionally; understand what happens when keys expire or are evicted.
- **Durability:** Decide whether Redis is a cache, coordination layer, or data store whose contents must survive restarts. Configure persistence and recovery accordingly.
- **Atomicity:** Individual commands are atomic, but multi-command workflows may require a transaction or script to preserve an invariant.
- **Availability and overload:** Account for Redis outages, hot keys, and memory pressure. A cache miss or unavailable Redis should have an intentional fallback behavior.
- **Clustering:** In Redis Cluster, multi-key operations can be constrained by hash-slot placement, so key design can affect which operations are possible.

## 6. Explaining project experience in an interview

Use an accurate example from your own experience:

> “In **[project/feature]**, I used Redis **[structure]** for **[use case]**. We chose it because **[operations/access pattern]** mattered. It helped with **[observed benefit]**. We also handled **[TTL, memory, duplicate processing, persistence, or failure behavior]**. We considered **[alternative]**, but chose this approach because **[trade-off]**.”

For example, if it matches your experience:

> “We used a Sorted Set for a leaderboard because we needed to rank members by score and query the top results efficiently. We set a retention policy for old entries and treated the relational database as the durable source of truth.”

Be prepared to discuss the access pattern, alternatives, operational trade-offs, and what happened when Redis was unavailable or data expired. Do not claim project experience you do not have.

## 7. Common interview questions

### Why use a Hash instead of a String containing JSON?

**Answer outline:** A Hash supports field-level operations without retrieving and replacing the whole value. A JSON string can be preferable when the application treats the document as a whole or uses JSON-specific capabilities. Compare command patterns, serialization, data size, and module/runtime support.

### When would you use a List versus a Stream?

**Answer outline:** A List can support simple push/pop queues or bounded histories. A Stream is more appropriate when entries need IDs, replay, consumer groups, and pending-entry tracking. Both require an explicit retention and failure-recovery strategy.

### Why use a Sorted Set for a leaderboard?

**Answer outline:** Members are unique and associated with scores, making score ordering and range queries natural. Discuss tie behavior, score updates, retention, and whether Redis or another store is the durable source of truth.

### Are Redis commands atomic?

**Answer outline:** Individual commands are atomic, but a sequence of commands is not necessarily atomic. Use a transaction or script when a correctness invariant spans multiple operations.

### Is Redis a durable database?

**Answer outline:** Redis can be configured with persistence, but whether it is an appropriate system of record depends on durability, recovery, consistency, and operational requirements. State whether it is being used as a cache, coordination layer, or primary store and design accordingly.

## 8. Mistakes to avoid

- Choosing a structure based only on the domain object rather than required operations.
- Treating Lists as a complete durable queue without retry and recovery design.
- Assuming a sequence of commands is atomic because each command is atomic.
- Using Redis as a source of truth without explicitly designing persistence, backups, and recovery.
- Ignoring memory limits, eviction, TTL behavior, hot keys, or cluster key placement.
- Claiming personal project experience that cannot be described accurately.

## 9. Quick revision

- **String:** simple value, cache, counter.
- **Hash:** field-value record with field-level operations.
- **List:** ordered sequence, simple queue, recent history.
- **Set:** unique members, membership, set algebra.
- **Sorted Set:** member plus score, ranking, score ranges.
- **Stream:** ordered entries, consumer groups, pending work, replay.
- **Bitmap:** compact boolean flags.
- **HyperLogLog:** approximate distinct counts.
- **Geospatial:** location and radius queries.
- Match the data structure to the access pattern.
- Individual commands are atomic; multi-command invariants need explicit coordination.
- Decide TTL, eviction, persistence, failure fallback, and memory budget up front.
