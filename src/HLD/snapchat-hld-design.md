# High-Level Design: Multimedia Messaging Platform (Snapchat-like)

> A design document capturing the system design worked through in the HLD coaching session.
> Covers real-time messaging, ephemeral snaps, 24-hour stories, media storage, notification
> delivery, and content expiration at scale.

---

## 0. Problem Statement

> Design a high-scale multimedia messaging platform like Snapchat. The system should support
> real-time messaging, ephemeral 'snaps' that disappear after being viewed, and a 'Stories'
> feature where content is available for 24 hours. Discuss the storage strategy for media,
> notification delivery, and the mechanism for content expiration at scale.

---

## 1. Requirements

### Functional
- Real-time 1:1 and group messaging (text + media).
- **Snaps** — ephemeral media that disappears after being viewed.
  - 1:1 snap: deleted on view, or after **31 days** if unopened.
  - Group snap: deleted when all members view it, or after **7 days** if unopened.
- **Stories** — media visible to friends for **24 hours**.
- Notifications for new messages, snaps, and posted stories.
- Read receipts, typing indicators, online/presence status.

### Non-Functional
| NFR | Target |
|---|---|
| Message delivery latency | P99 < 300 ms |
| Media upload latency | P99 < 3 s |
| Media download latency | P99 < 1 s |
| Availability | 99.99% |
| Deletion guarantee | Hard delete + CDN purge + short TTL |
| Message ordering | Per-conversation (not global) |
| Durability | No data loss after confirmed write |
| Privacy | Snaps must truly disappear (no soft delete) |

### Out of Scope
AR filters/lenses, Snap Map, Discover/ads, video calling, Memories archive,
user registration, friend management.

---

## 2. Capacity Estimation

**Assumptions:** 10M total users, 1M DAU, 100M messages/day, 10M media items/day,
~2 MB average media size, ~5:1 read-to-write ratio.

| Metric | Value |
|---|---|
| Text storage/day | ~100 GB |
| Media storage/day | ~20 TB |
| Media storage/year | ~7 PB |
| Peak message write QPS | ~5,000 |
| Peak message read QPS | ~20,000 |
| Peak media upload QPS | ~500 |
| Peak media download QPS | ~2,500 |
| Peak ingress bandwidth | ~1 GB/s |
| Peak egress bandwidth | ~5 GB/s |

> Egress ≈ 5× ingress (one upload → many downloads) → CDN is mandatory for reuse-heavy content.

---

## 3. API Design

| Endpoint | Method | Purpose |
|---|---|---|
| `/media/upload` | POST | Upload media, returns `mediaId` (two-phase upload) |
| `/messages` | POST | Send text/media message to a `conversationId` |
| `/snaps` | POST | Send a snap (references `mediaId`) |
| `/snaps/{snapId}` | GET | Fetch snap content (metadata + CDN URL) |
| `/snaps/{snapId}/viewed` | POST | Client confirms render → triggers deletion |
| `/stories` | POST | Post a story |
| `/stories/{userId}` | GET | Fetch a friend's stories |

**Key API decisions**
- **Client-generated idempotency key** — deduplicates retries, prevents duplicate messages under at-least-once delivery.
- **`conversationId` abstraction** — unifies 1:1 and group chats; no `isGroupChat` flag.
- **Two-phase media upload** — upload media first (get `mediaId`), then send message referencing it. Avoids large payloads in the message API and partial-upload corruption.
- **Server-authoritative timestamps** — do not trust client clocks.
- **Snap "viewed" = client render confirmation**, with a server-side TTL safety net for malicious/non-confirming clients.

---

## 4. Data Model

| Data | Store | Rationale |
|---|---|---|
| Users, friendships, groups | **PostgreSQL** | Relational, joins, referential integrity |
| Messages | **Cassandra** | Write-heavy, append-only; LSM-tree suits high write throughput |
| Snap/Story metadata | **Cassandra** | Native TTL for auto-expiration; cheap disk at volume |
| Media metadata | **Cassandra** | Simple key lookup by `mediaId` |
| Media files | **S3** | Blob storage + lifecycle policies for deletion |
| Connection registry (`userId → WS server`) | **Redis** | Fast, ephemeral key-value |
| Presence / typing indicators | **Redis Pub/Sub** | Ephemeral, loss-tolerant, sub-ms |
| Unread counters | **Redis** (backed by Cassandra) | Frequent updates, fast login reads |

**Messages schema (Cassandra)**
```
partition key: (conversationId, time_bucket)   -- e.g. time_bucket = "2026-08"
clustering key: timestamp
```
> Time-bucketing prevents unbounded partitions for long-running conversations.

**Group snap view tracking (Cassandra)**
```
partition key: snapId
clustering key: userId
columns: viewed_at
```
> Delete when `count(views) == group_member_count` (cache member count) OR TTL expires — whichever first.

---

## 5. Architecture Overview

```
                         ┌──────────────┐
                         │  CDN / Edge  │  media downloads (short TTL)
                         └──────┬───────┘
                                │
┌────────┐              ┌───────┴────────┐
│ Client │── HTTP ─────▶│  API Gateway   │──▶ Load Balancer
│        │── WebSocket ▶│                │
└────────┘              └───────┬────────┘
              ┌─────────────────┼───────────────────┐
              ▼                 ▼                    ▼
      ┌──────────────┐  ┌──────────────┐   ┌───────────────────┐
      │ Chat Service │  │ Snap/Story   │   │ WebSocket Service │
      │              │  │ Service      │   │ (real-time push)  │
      └──┬───────────┘  └──┬───────────┘   └─────────┬─────────┘
         │  │  │           │  │  │  │                 │
         ▼  ▼  ▼           ▼  ▼  ▼  ▼          ┌──────┴───────┐
      Cassandra Redis   Cassandra S3 Kafka     │ Redis Pub/Sub│
      Kafka  Postgres                          │ + Presence   │
                                               │ + Conn Reg   │
                              ┌──────────────┐ └──────────────┘
                              │ User Service │──▶ PostgreSQL
                              └──────────────┘
                              ┌──────────────────┐
                     Kafka ──▶│ Notification Svc │──▶ APNs / FCM
                              └──────────────────┘
                     Kafka ──▶│ Cleanup Consumer │──▶ S3 / CDN / Cassandra
                              └──────────────────┘
```

**Services**
- **Chat Service** — messaging APIs; write-heavy, connection-heavy.
- **Snap/Story Service** — ephemeral content, TTL management, media fan-out.
- **User Service** — profiles, friends, groups (relational).
- **WebSocket Service** — holds live client connections; pushes real-time events.
- **Notification Service** — Kafka consumer; push via APNs/FCM.
- **Cleanup Consumer** — Kafka consumer; deletes expired/viewed media from S3, purges CDN, removes metadata.

**Connectivity choices**
- **Real-time delivery:** Backend → **Redis Pub/Sub** → WebSocket Service → Client (fire-and-forget; durable copy already in Cassandra).
- **Group fan-out:** per-group Pub/Sub channel — one publish reaches all subscribed WS servers.
- **Async work via Kafka:** snap deletion (retryable), media processing (thumbnail/compress), story fan-out, notifications.
- **Presence via Redis Pub/Sub** — not Kafka (ephemeral, loss-tolerant).
- **CDN: pull-based** — cache-on-first-request.

---

## 6. Critical Flows

### Send a snap (User A → User B online)
1. Client captures photo → `POST /media/upload` → Media Service → **S3** + **Cassandra** (metadata) → returns `mediaId`.
2. Client → `POST /snaps {senderId, receiverId, mediaId}` → Snap Service.
3. Snap Service → **Cassandra** (snap metadata, TTL) → **Redis** (increment unread) → **Redis Pub/Sub** (real-time alert) → **Kafka** (`snap.created`).
4. WebSocket Service pushes alert (if online); Notification Service sends push (if backgrounded).

### View + delete a snap (User B)
1. Client taps snap → `GET /snaps/{snapId}` → Snap Service → Cassandra (metadata + CDN URL).
2. Client fetches media from **CDN** (pulls from S3 on miss) → renders.
3. Client → `POST /snaps/{snapId}/viewed`.
4. Snap Service → **Cassandra** mark viewed (SYNC, prevents re-view) → **Redis** decrement unread (SYNC) → **Kafka** `snap.viewed` (ASYNC).
5. Cleanup Consumer → delete S3 object → purge CDN → delete Cassandra metadata (retried on failure).

> Sync boundary is step 4 (mark viewed). Everything after is async garbage collection.

---

## 7. Content Expiration Strategy

| Content | Trigger | Mechanism |
|---|---|---|
| 1:1 snap | View OR 31-day TTL | Event-driven delete + Cassandra TTL |
| Group snap | All viewed OR 7-day TTL | View-count check + Cassandra TTL |
| Story | 24-hour TTL | Cassandra TTL + S3 lifecycle policy |

- **Cassandra TTL** auto-expires metadata (spread across compaction cycles — no thundering herd).
- **S3 lifecycle policies** auto-delete media (e.g. objects older than 25 h for stories).
- **Short CDN TTL** avoids stale edge cache; active CDN purge on view for snaps.
- **No custom sweeper** required for the common path — infrastructure handles pacing.

---

## 8. Consistency Model

| Data | Consistency | Rationale |
|---|---|---|
| Snap viewed flag | **Strong** (QUORUM R+W) | Must prevent re-viewing across devices (privacy) |
| Message ordering | **Strong** per conversation | Out-of-order breaks conversations |
| Story visibility | Eventual | 1–2 s delay is imperceptible |
| Story view count | Eventual | Approximate is fine |
| Presence / typing | Eventual | Ephemeral, loss-tolerant |
| Unread counts | Eventual | Off-by-one temporarily acceptable |
| Read receipts | Eventual | Slight delay acceptable |

---

## 9. Reliability & Failure Handling

- **Kafka down on emit:** Outbox pattern — write event to outbox table atomically with data; background poller publishes when Kafka recovers. Pull-on-reconnect is a secondary safety net.
- **Media Service crash after S3 write, before metadata:** Upload to `s3://uploads/pending/` then move to `confirmed/` on metadata success; S3 lifecycle deletes stale `pending/` objects. Client retry is safe (S3 PUT idempotent by key).
- **Offline recipients:** Cassandra is source of truth; Redis Pub/Sub is only a real-time optimization. Client pulls missed messages on reconnect using per-conversation unread counters + `last_read_timestamp`.
- **Celebrity fan-out (5M followers):** Hybrid fan-out — fan-out-on-write for regular users, fan-out-on-read for celebrities (`friend_count > ~10K`). Separate Kafka topics (`high-priority` vs `bulk`) so celebrity fan-out cannot starve normal notifications.

---

## 10. Key Design Trade-offs (Summary)

- **Cassandra over sharded Postgres for messages** — LSM-tree write throughput + native horizontal scaling; gives up joins & default strong consistency.
- **Redis Pub/Sub over Kafka for WS routing** — transient real-time signal; durability unnecessary since Cassandra holds the durable copy.
- **CDN differentiated by access pattern** — worth it for stories/group snaps (reuse); 1:1 snaps served from S3 directly (near-zero cache hit).
- **Microservices** — justified by divergent scaling/storage profiles; a modular monolith is viable at 1M DAU (start monolith, extract under growth).
- **Cassandra over persistent Redis for snap metadata** — disk cost at ~300M+ live records vs RAM; latency difference (ms vs sub-ms) is irrelevant here.

---

## 11. Areas Requiring Deeper Dive

Prioritized list of topics that were only partially explored or deserve dedicated study:

### High priority
1. **WebSocket connection management at scale**
   - Connection registry consistency (Redis) vs server crashes; reconnection storms; sticky sessions vs stateless gateways; heartbeat/keepalive tuning; horizontal scaling of WS fleet.
2. **Group snap "viewed by all" coordination**
   - Race conditions on concurrent views; caching/refreshing group member count; handling membership changes mid-snap; the "49 of 50 viewed" edge case.
3. **Kafka topology & delivery guarantees**
   - Partitioning strategy (by `userId` vs `conversationId`), consumer group sizing, exactly-once vs at-least-once, DLQ handling, rebalancing behavior, priority-topic isolation for celebrity fan-out.
4. **Media pipeline (upload → transcode → deliver)**
   - Multipart/resumable uploads, thumbnail generation, video compression/transcoding, format/resolution variants, pre-signed URLs, S3 Transfer Acceleration for 1:1 snaps.
5. **True deletion & privacy guarantees**
   - Ordering of S3 delete / CDN purge / metadata delete; orphan detection; CDN purge propagation delay; verifying no residual copies (backups, replicas).

### Medium priority
6. **Caching layer design**
   - What to cache (recent messages, conversation metadata, user profiles), cache-aside invalidation correctness, hot-conversation handling, cache stampede prevention.
7. **Cassandra data modeling depth**
   - Time-bucket sizing (weekly vs monthly), tombstone accumulation from TTL/deletes, compaction strategy (TWCS for TTL-heavy data), read/write consistency levels per query.
8. **Notification fan-out & batching**
   - Batching windows, dedup, push-token management, APNs/FCM rate limits and retries, delivery receipts.
9. **Presence system**
   - Heartbeat frequency vs accuracy vs cost, presence propagation to friends, scaling Redis Pub/Sub, last-seen semantics.
10. **Multi-region & disaster recovery**
    - Data locality for latency, cross-region replication (Cassandra, S3), failover, conflict handling, regional CDN strategy.

### Lower priority / cross-cutting
11. **Observability** — metrics (QPS, latency percentiles, queue lag), tracing across services, SLOs/alerts, media-pipeline monitoring.
12. **Security** — auth/authz on media URLs (pre-signed, short-lived), encryption at rest/in transit, PII handling, abuse/rate limiting, snap-screenshot detection.
13. **Cost optimization** — S3 storage tiers, CDN egress cost, Cassandra cluster sizing, RAM footprint of Redis.
14. **Capacity planning & load testing** — peak-hour modeling, autoscaling policies, backpressure handling.
15. **Communication/trade-off articulation (personal focus)** — practice the "chose X because / trade-off is / reconsider if" structure; database internals (LSM vs B-tree); CDN cache-hit reasoning; RAM-vs-disk cost reasoning.

---

*Generated from HLD coaching session. Use the deep-dive list to drive follow-up practice.*
