# How Redis Works Internally — Staff Backend Engineer Interview Notes

## 1. Staff-level summary

Redis is a networked, in-memory data-structure server. It parses commands from clients, executes operations against data structures held in memory, and returns protocol replies. Its speed comes from keeping hot data in memory, efficient purpose-built operations, and a simple command execution model—not from being “just a cache” or from every part of the server running on one thread.

## 2. Mental model

```text
Client command
  → network I/O and RESP parsing
  → command validation and dispatch
  → data-structure operation in the keyspace
  → reply encoding and network write
  → optional persistence / replication work
```

Redis is best understood as an **in-memory data-structure engine with optional durability and distributed deployment features**. A Redis deployment can be used as a cache, a coordination component, a transient store, or—if configured and operated appropriately—as a data store. Those roles have different durability and recovery expectations.

## 3. Why it matters in backend interviews

Understanding the internals helps explain:

- Why many common operations have low latency
- Why a single expensive command can affect other clients
- How data types influence both API shape and memory use
- Why TTL and eviction are not the same thing
- What RDB, AOF, replication, Sentinel, and Cluster do—and do not guarantee
- How memory pressure, hot keys, and network limits affect production behavior

## 4. Command execution and concurrency

### Request lifecycle

Redis clients generally send commands using **RESP**, Redis’s serialization protocol. The server reads bytes from client connections, parses commands, validates them, dispatches the command, and writes the result back in RESP.

Commands are generally atomic with respect to other commands: another client does not observe a command halfway through its execution. This does **not** make a sequence of separate commands atomic.

### Event-driven execution

Redis uses an event-driven model. Command execution has traditionally been largely single-threaded, which simplifies coordination over in-memory data and avoids many lock costs. Modern Redis versions can use I/O threads for parts of network read/write processing, and background threads/processes handle selected work such as persistence-related tasks and lazy freeing. Exact behavior depends on Redis version and configuration.

Do not describe Redis as “entirely single-threaded.” The important distinction is that ordinary command execution against the keyspace is designed around serialized command processing, while some I/O and background work can use additional threads.

### Implication: command latency affects others

A long-running command can occupy command execution and delay other clients. Prefer bounded operations, pagination, batching where appropriate, and background/streaming approaches for large work. Inspect command latency and avoid unbounded key scans or huge collection responses on latency-sensitive paths.

## 5. In-memory keyspace and data structures

The keyspace maps keys to Redis objects. Each value has a logical type—such as String, Hash, List, Set, Sorted Set, or Stream—and an internal representation chosen to support operations efficiently.

Examples of internal implementation ideas include:

- **Strings:** Redis uses a Simple Dynamic String (SDS) representation for general string values and key storage.
- **Hashes and Sets:** May use compact encodings for small values and hash tables for larger or more complex ones.
- **Lists:** Commonly represented by a linked structure of packed nodes (quicklist/listpack in current implementation families).
- **Sorted Sets:** Combine member lookup with score ordering; common representations use a dictionary and a skip list for larger sorted sets.
- **Streams:** Use structures optimized for ordered entries and consumer metadata.

Internal encodings and conversion thresholds are implementation details that can change by version. The practical lesson is that the same logical type may use different layouts depending on data size and operations.

### Complexity and data shape

Redis commands have different time and memory characteristics. A command that touches one key can still be expensive if it scans or returns a very large collection. Understand command complexity, cardinality, response size, and whether the operation blocks or monopolizes the server.

### Memory allocation

Redis stores active data in RAM, so memory is a capacity boundary. Per-key overhead, object metadata, allocator fragmentation, replication buffers, persistence copy-on-write, and client buffers can make process memory exceed raw payload size. Allocator behavior depends on the build and platform.

## 6. Expiration and eviction

### TTL expiration

Keys can have a time-to-live (TTL). Expiration is handled through a combination of:

- **Passive expiration:** A key is checked when accessed.
- **Active expiration:** The server periodically samples keys with expiry and removes expired keys.

Therefore, an expired key may not be physically removed at the exact millisecond its TTL reaches zero, though it should be treated as logically expired.

### Eviction under memory pressure

When a configured `maxmemory` limit is reached, Redis applies the configured eviction policy, if one is enabled. Policies differ in whether they evict keys with TTLs, least-recently-used/frequently-used candidates, or any eligible key.

**Expiration** is a key’s time-based lifecycle. **Eviction** is memory-pressure behavior. A cache design should account for both and should not rely on eviction as its only correctness or cleanup mechanism.

## 7. Persistence

Redis can operate without persistence, but persistence options affect recovery point, restart time, I/O, and memory.

### RDB snapshots

RDB saves a point-in-time dataset snapshot. It can provide compact backups and efficient startup in many workloads, but changes since the last snapshot may be lost after a failure.

Snapshot creation commonly uses `fork` on supported operating systems. The child writes the snapshot while the parent continues serving commands. Copy-on-write means pages modified by the parent after the fork may require additional memory while the snapshot is being written.

### AOF (Append Only File)

AOF records write operations so the dataset can be reconstructed by replaying them. Durability depends on the configured fsync policy: syncing every write, periodically, or relying more heavily on operating-system buffering has different latency and data-loss trade-offs.

AOF rewrite compacts the log by producing a more concise representation of the current dataset. Rewrite activity can consume CPU, I/O, and additional temporary disk/memory resources.

### RDB and AOF together

Some deployments use both mechanisms to balance backup/recovery properties. Neither should be assumed to provide zero data loss in every failure scenario. Validate the configuration, storage durability, backup handling, and restore procedure against the recovery point objective (RPO) and recovery time objective (RTO).

## 8. Replication and high availability

### Primary-replica replication

Redis replication is generally asynchronous. Replicas receive the primary’s data changes and can serve some read workloads, but replication lag means a replica may be stale. A successful write response from the primary does not automatically mean every replica has durably received the write.

### Sentinel

Redis Sentinel monitors primary/replica deployments and can coordinate failure detection and promotion. It supports availability management, but failover is not equivalent to a strongly consistent consensus-based database guarantee; clients must handle reconnects, topology changes, and potential write loss around failures.

### Redis Cluster

Redis Cluster partitions the keyspace across nodes using **16,384 hash slots**. Keys map to slots, and slots map to cluster nodes. Hash tags can place related keys in the same slot so supported multi-key operations can run together.

Cluster provides sharding and failover mechanisms, but introduces topology awareness, resharding, cross-slot constraints, and additional client behavior. It does not make arbitrary multi-key operations globally transactional.

## 9. Trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| In-memory primary data | Very low-latency operations | RAM cost and finite capacity | Workload needs low latency and fits memory budget |
| RDB snapshots | Compact point-in-time persistence | Potential loss of writes since snapshot; fork/COW resource cost | Backups and recovery where snapshot interval is acceptable |
| AOF persistence | Better write-history recovery depending on fsync policy | Write/I/O overhead, replay and rewrite complexity | Recovery requirements justify the durability trade-off |
| Replicas for reads | Distributes read traffic and provides failover candidates | Stale reads, lag, and operational complexity | Read-heavy workloads tolerate eventual replication |
| Cluster sharding | Scales keyspace and throughput across nodes | Cross-slot limits and more complex operations | Dataset or throughput exceeds a single node’s capacity |
| Eviction policy | Controls memory use automatically | Data can disappear under pressure | Cache data is recomputable and misses have safe behavior |

## 10. Common failure modes and mitigations

| Symptom | Likely cause | Mitigation |
|---|---|---|
| Latency spike for many clients | Expensive command, large response, CPU saturation, or fork activity | Inspect slow log/latency tools and command patterns; bound work |
| `OOM` or writes rejected | `maxmemory` reached with no suitable eviction behavior, or memory overhead underestimated | Capacity plan, inspect memory usage, choose policy, reduce/expire data |
| Unexpected cache misses | TTL expiry or eviction | Measure miss rate, tune capacity/TTL, ensure safe fallback |
| Memory rises during snapshot | Copy-on-write while pages change after fork | Leave headroom, reduce write pressure during snapshot, test dataset and platform behavior |
| Stale reads | Replica lag | Route consistency-sensitive reads to primary or define acceptable staleness |
| Recent writes missing after failover | Asynchronous replication/failover window | Set realistic durability expectations; use persistence and application-level recovery appropriate to RPO |
| Multi-key command fails in Cluster | Keys hash to different slots | Redesign key layout/hash tags where the invariant justifies co-location |
| One shard is overloaded | Hot key or skewed key distribution | Measure per-shard/key load; redesign access pattern or distribute the hot workload |

## 11. Observability and operations

Monitor:

- Command latency and slow commands
- CPU, memory, fragmentation, and memory by key/type where practical
- Evictions, expirations, rejected connections/commands, and keyspace hit rate
- Client count, output buffers, and network throughput
- Persistence status, snapshot duration, AOF rewrite, and disk health
- Replication offset/lag and failover events
- Cluster slot distribution and per-node load

Use Redis’s diagnostic commands and slow-log/latency facilities carefully; avoid running large scans or expensive diagnostics against a production node without understanding their impact. Test backups by restoring them, not only by checking that files exist.

## 12. Staff-level talking points

> Redis is fast because it keeps data in memory and offers operations designed for specific structures. Its command execution model is serialized for keyspace operations, while network I/O and background tasks may use additional threads depending on version and configuration.

> I evaluate Redis with a memory and recovery budget, not just a latency benchmark. I include object overhead, replication, client buffers, persistence copy-on-write, eviction behavior, and the recovery point the business can tolerate.

> Replication improves availability and can scale reads, but it is asynchronous, so I do not assume replica reads are current or that failover guarantees zero loss. I define which operations can tolerate staleness and test failover and restore paths.

> I also inspect command complexity and response size. A single unbounded command can affect all clients even when request volume is low.

## 13. Common interview questions

### Is Redis single-threaded?

**Tests:** Whether the candidate avoids oversimplification.

**Answer outline:** Keyspace command execution is designed around serialized processing, but modern versions can use I/O threads and background work uses other execution contexts. State the version/configuration caveat and explain that long commands can still affect other clients.

### How does Redis expire keys?

**Tests:** Understanding TTL behavior.

**Answer outline:** Redis uses passive checks on access and active sampling/removal. Expiry is logical at the deadline, but physical reclamation may happen later.

### What is the difference between RDB and AOF?

**Tests:** Persistence trade-offs.

**Answer outline:** RDB is a point-in-time snapshot; AOF records write operations and replay behavior depends on fsync policy. Compare RPO, restart, I/O, disk, and fork/rewrite costs.

### Does replication make Redis strongly consistent?

**Tests:** Distributed-system correctness.

**Answer outline:** No. Replication is generally asynchronous, so replicas may lag and failover can lose recently acknowledged writes. State the application’s staleness and RPO needs.

### Why can a Redis command cause latency for unrelated clients?

**Tests:** Execution and complexity awareness.

**Answer outline:** A costly command or huge response can occupy command execution or consume CPU/network resources. Bound work, inspect complexity, and use pagination or incremental/background processing.

## 14. Mistakes to avoid

- Saying Redis is entirely single-threaded in all versions and configurations.
- Treating Redis as only a cache or assuming RAM is the only memory cost.
- Assuming TTL deletes a key at an exact instant.
- Confusing eviction with expiration.
- Claiming AOF or replication guarantees zero data loss without configuration and failure assumptions.
- Assuming replica reads are always current.
- Using Cluster without planning multi-key slot placement and client behavior.
- Running unbounded scans or large result operations on a latency-sensitive server.

## 15. Quick revision

- Redis parses RESP commands, dispatches operations, and replies over client connections.
- Keyspace commands are generally serialized; I/O and background work may use threads.
- Logical data types may have different internal encodings based on size and version.
- A long or large command can affect other clients.
- TTL expiration uses passive and active mechanisms; eviction handles memory pressure.
- RDB is snapshot persistence; AOF records write operations with configurable fsync behavior.
- Fork-based persistence can incur copy-on-write memory cost.
- Replication is generally asynchronous; replicas can be stale.
- Sentinel coordinates failover; Cluster shards keys across 16,384 hash slots.
- Plan memory, command complexity, RPO/RTO, key distribution, and recovery—not just average latency.
