# High-Level Design: Distributed API Rate Limiter

> HLD coaching session document. Captures the full design worked through in guided mode,
> including requirements, capacity estimation, algorithm choice, data model, architecture,
> failure handling, consistency, the key trade-offs, and the session evaluation.

---

## 0. Problem Statement

> Create a distributed API rate limiter system that regulates and restricts the volume of
> incoming requests to a group of APIs from numerous clients.

**Requirements:**
1. **Rate Limiting** — restrict the number of requests each client can make within a time window (X requests per Y seconds).
2. **Distributed and Scalable** — rate limiting across multiple servers/nodes for high request volumes.
3. **Efficient and Low Latency** — minimal impact on API response time.
4. **Dynamic Adjustment** — adjust rate limits for specific clients/APIs without system restarts.
5. **Fairness and Prioritization** — prevent a single client from monopolizing resources; support prioritization for critical clients/APIs.
6. **Metrics and Monitoring** — track incoming request rate, allowed requests, and violations.
7. **Resilience and Fault Tolerance.**

---

## 1. Requirements (clarified)

### Functional
- Rate limiter enforced **in-process at the API gateway** (decision reached in Step 3; see §3).
- **Client identity:** User ID, IP address, or API key.
- **Scope:** limit on **all** of — per-client-global, per-client-per-API, and per-API-global.
- **On breach:** return **HTTP 429 Too Many Requests** with a **`Retry-After`** header.

### Non-Functional
| NFR | Target / Decision |
|---|---|
| Latency added to hot path | ≤ 10 ms ceiling (aim sub-ms where possible) |
| Accuracy | **Small overshoot acceptable** (not strictly exact) — the pivotal trade-off |
| Availability | 99.99% |
| Failure mode | **Fail-open by default** (limiter outage must not take down APIs); **fail-closed per-policy** for critical/paid endpoints |

### Scale
- 100M DAU, ~1B requests/day.
- **Sanity check:** 1B/day ÷ 86,400 ≈ **~11.6K req/s average**; peak (3–5×) ≈ **~35K–60K req/s**.
- (Note: 100M DAU × 1B/day implies only ~10 req/user/day — flagged as unrealistically low; the QPS figure is what drives the design.)

---

## 2. Capacity Estimation

### Counter count — estimate the ACTIVE working set, not the key space
- Naive (wrong): 10M clients × 1,000 APIs = **10B** cartesian combinations.
- **Correct:** counters only exist for **active (client, API) pairs within the current window**, and **expire via TTL**. If 10M active clients each hit ~10 distinct APIs → **~100M live counters** (≈1000× smaller than the cartesian product).

### Memory
- 100M counters × ~100 bytes ≈ **~10 GB** → fits comfortably in a Redis cluster.

### QPS against the counter store
- Limiting on **all three scopes** ⇒ up to **3 counter ops per request**.
- 50K req/s × 3 ≈ **~150K ops/s** against the store → shard for QPS + HA.

> **Lesson:** always size the *active TTL'd working set*, and sanity-check scale numbers by dividing.

---

## 3. Algorithm & Key Decisions

### Rate-limiting algorithm: **Sliding Window Counter**
- Industry sweet spot: near-perfect accuracy without the memory cost of a sliding-window **log**.
- Keeps current + previous window buckets; old buckets expire via TTL.

### Enforcement location: **In-process at the API gateway** (not a standalone service)
Hop analysis on the critical path (runs on every request):
```
(a) Standalone service:   Gateway → [net] → Limiter Svc → [net] → Redis → back   (2 extra legs)
(b) In-process @ gateway: Gateway (logic here) → [net] → Redis → back            (1 leg; 0 for local/approx)
```
- A standalone limiter service would just front Redis → pure added latency + a critical-path dependency for no functional gain.
- **Chosen:** embed limiter logic in the gateway, backed by shared Redis.
- **Reconsider standalone** if many *heterogeneous* clients (gRPC services, batch jobs, third parties — not just the gateway fleet) must share the exact same limiter, making the central hop worthwhile.

### Fail-open vs fail-closed
> "I fail-open because the limiter shouldn't be a single point of failure for the whole platform. The trade-off is losing protection during an outage. I'd fail-closed for specific critical/paid endpoints (payments, auth) where unlimited traffic is worse than blocking. So it's per-policy, defaulting to fail-open."

---

## 4. Data Model & Sharding

| Data | Store | Sharding / Lifecycle |
|---|---|---|
| Per-client & per-client-per-API counters | **Redis Cluster** | **Sharded by client key** → co-locates a client's counters on one node → atomic, local, low-latency checks |
| Per-API-global counter (hot) | **Local in-gateway count + async aggregation** | Not in shared Redis on the hot path (avoids hot key) |
| Counter lifecycle | — | **TTL-based auto-expiry** tied to the window (not manual reset) → bounded memory |
| Policy config | **Policy DB** + in-gateway cache | Invalidated via **pub/sub** on update |

### Why shard by client key
> The two highest-volume scopes are keyed by client. Co-locating them keeps each check atomic and local, protecting the 10 ms budget.

---

## 5. The Hot-Key Problem (per-API-global counter)

Sharding by client can't hold a counter touched by *all* clients (e.g., "total requests to `/search`"). Pinning it to one node = **hot partition**.

### Technique 1 — Counter sharding (write-sharding)
- Split `api:/search` into `shard0..shardN-1`; increment a random shard; total = sum of shards.
- Write load spread; **read cost = N reads** to sum. (How DynamoDB/Cassandra handle hot counters.)

### Technique 2 — Local counting + async aggregation (**chosen**)
- Each node keeps a **local count**; pushes deltas to an aggregator every ~100ms–1s; aggregator broadcasts the global total; nodes enforce using last-known global view + local count.
- **Enforcement is fully local → sub-ms, no hot-path network hop.**
- **Approximate** — bounded overshoot during the sync window (explicitly allowed).

### Overshoot math (worked example)
- Global limit = 10,000 req/s, 50 nodes, sync interval = 1s.
- **Naive worst case:** every node enforces the full 10K locally on a stale "count = 0" view → **50 × 10,000 = 500,000** could slip through (a 50× overshoot).

### Mitigations to bound overshoot
| Mitigation | Effect |
|---|---|
| Shorter sync interval (1s → 50ms) | ~20× less slippage |
| **Per-node quota** (limit/N = 10K/50 = 200/s) | Bounds overshoot to ~one interval of real traffic |
| Per-node quota + **rebalancing** unused quota | Best accuracy; busy nodes borrow spare quota |

### Structured justification (final)
> "For the per-API-global limit I choose **local counting with per-node quotas + async rebalancing** because it solves the hot-key/partition problem and keeps enforcement local (sub-ms). The trade-off is **bounded overshoot / eventual consistency** — requests can slip past during the sync window. I'd reconsider (switch to **central atomic counting** with sharded `INCR`) if the global limit had to be **exact** — e.g., a hard billing/quota cap where every overage costs money or breaks a contract."

> **Senior insight:** different scopes get different strategies. Per-client → sharded, exact, atomic. Per-API-global → local, approximate, hot-key-safe.

---

## 6. API Design

### Hot-path check (runs in-gateway; shown as logical contract)
```
checkLimit(clientId, apiId) → { allowed: bool, remaining, retryAfter }
```
- Response carries `remaining` + `retryAfter` so the caller can emit `429 + Retry-After`.

### Admin / dynamic config (Requirement 4)
```
PUT /policies/{clientId}   { id, name, limit, window, tier, priority }
```
- **Propagation:** on update, publish `policy.changed(clientId)` via **pub/sub**; all gateway nodes evict that cached policy key.
- **Pub/sub chosen over short-TTL** because the requirement demands *dynamic, no-restart* changes → near-instant propagation and no stale-window; TTL would serve old limits until expiry.

---

## 7. Architecture Overview

```
                 ┌──────────────────────────────────────────────┐
   Clients ─────▶│              API Gateway fleet               │
                 │  (rate-limiter logic runs IN-PROCESS here)   │
                 │   • per-client / per-client-per-API checks   │
                 │   • per-API-global: local count + aggregation│
                 │   • policy cache (pub/sub invalidated)       │
                 │   • local block-verdict cache (short-circuit)│
                 │   • local metric counters (async flush)      │
                 └───┬───────────────┬───────────────┬──────────┘
                     │               │               │
             1 hop   │        0 hop  │        async  │
                     ▼               ▼               ▼
              ┌────────────┐  ┌──────────────┐  ┌──────────────────┐
              │Redis Cluster│  │ Global-limit │  │ Metrics pipeline │
              │(counters,   │  │ aggregator   │  │ StatsD/Prometheus│
              │ sharded by  │  │ (async delta │  │ + ELK (violation │
              │ client, TTL,│  │  sync +      │  │  audit logs)     │
              │ replicated) │  │  rebalance)  │  └──────────────────┘
              └────────────┘  └──────────────┘

   Admin ─▶ Policy Service ─▶ Policy DB ──(pub/sub: policy.changed)──▶ all gateway nodes
```

**Components**
- **API Gateway (in-process limiter):** enforces all scopes; 0–1 network hops per check.
- **Redis Cluster:** per-client counters, sharded by client key, TTL expiry, **primary–replica replication** for HA.
- **Global-limit aggregator:** collects per-node deltas, maintains approximate global totals, rebalances per-node quotas.
- **Policy Service + DB:** source of truth for limits/tiers; pushes invalidations via pub/sub.
- **Metrics pipeline:** local aggregation → async flush → StatsD/Prometheus (counters/alerts); ELK for violation audit logs.

---

## 8. Fairness & Prioritization (Requirement 5)

- **Fairness = isolation:** per-client buckets mean one client can't consume another's quota. Backstop the shared origin with the **per-API-global limit**. Size per-client limits so their sum respects backend capacity.
- **Prioritization = tiered policies + load-shed order:**
  - Premium clients get higher limits (`tier` field on policy).
  - Under backend saturation, **shed low-priority traffic first** (drop free-tier before paid) via a `priority` field + per-tier pools.
  - Optional **weighted fair queuing** to allocate capacity by weight.

---

## 9. Metrics & Monitoring (Requirement 6)

> **Principle:** the hot path must never block on metrics.
- Each gateway node **increments in-memory counters locally** (received / allowed / blocked per client+API).
- **Async flush** aggregates every few seconds to the metrics pipeline.
- **StatsD/Prometheus** for counters + alerting; **Kafka → aggregator** for high-cardinality; **ELK** for violation/forensic logs.
- **Lead with the mechanism** (local aggregate + async flush), tools are secondary.

---

## 10. Consistency

| Aspect | Model | Rationale |
|---|---|---|
| Per-client limit | Near-exact (atomic Redis op) | Low volume per client; correctness cheap |
| Per-API-global limit | **Eventual / approximate** | Hot key; overshoot bounded by sync interval + per-node quota |
| Counter durability on failover | **Async replication** (may lose last increments) | Consistent with the overshoot tolerance already accepted |
| Policy changes | Strong-ish via pub/sub invalidation | "Dynamic, no-restart" requirement |

---

## 11. Reliability & Failure Handling

### Redis shard dies (fail-open)
1. Checks for those clients **fail-open** → requests allowed while the shard is down.
2. On recovery, counters are lost/stale → affected clients briefly get **extra allowance**. **Acceptable** because overshoot is already tolerated.
3. **Resilience:** Redis **primary–replica replication + automatic failover** (Cluster/Sentinel). Replication is **async**, so a few increments may be lost on promotion — again within the accuracy model.
> **Do NOT** persist every increment to a durable DB — counters are high-churn, ephemeral, loss-tolerant; a disk write per request would break the latency budget. Replication (in-memory) is the right resilience mechanism.

### Hot client (noisy neighbor) — one client sends 20K req/s to one shard
1. **Breaks:** the buggy client's flood concentrates on its shard, impacting **other clients on that shard** (single-key writes are single-threaded in Redis).
2. **Mitigation — local short-circuit / negative caching:** once the gateway sees a client is over its limit, it **caches the "blocked" verdict locally** (short TTL) and rejects subsequent requests **with 0 Redis calls** → the flood never reaches the shard. Combine with **atomic `INCR`** and **gateway load-shedding** for egregious offenders.
> **Principle:** an over-limit client should be cheap to reject — don't pay Redis cost to repeatedly say "no."

---

## 12. Key Trade-offs (Summary)

- **In-process limiter over standalone service** — removes an extra hop on every request; reconsider if many heterogeneous clients must share one limiter.
- **Sliding window counter over sliding-window log** — near-exact accuracy at far lower memory.
- **Shard by client key** — atomic, local per-client checks; but can't host the per-API-global counter (→ local aggregation instead).
- **Local + approximate for hot global limit** — sub-ms, hot-key-safe; accepts bounded overshoot. Switch to central atomic counting only if the limit must be exact (billing caps).
- **Fail-open default, fail-closed per-policy** — availability of the platform vs protection of critical endpoints.
- **Async replication over durable persistence for counters** — resilience without wrecking latency; consistent with overshoot tolerance.
- **Metrics: local aggregate + async flush** — observability without hot-path latency.

---

## 13. Session Evaluation

### Interview Score
| Category | Score |
|---|---:|
| Requirements | 7/10 |
| Capacity estimation | 5/10 |
| API design | 6/10 |
| Data modeling | 7/10 |
| Architecture | 6/10 |
| Scalability | 6/10 |
| Reliability | 7/10 |
| Consistency | 7/10 |
| Failure handling | 6/10 |
| Trade-offs | 6/10 |
| Observability | 4/10 |
| Communication | 6/10 |
| Senior-level thinking | 6/10 |

### Strong Points
- Independently reasoned the **500K overshoot** calculation correctly.
- **Client-key sharding** — correct and well-justified when pushed.
- **Fail-open with per-endpoint nuance** absorbed and reused.
- **Trade-off structure** measurably improved vs the previous (Snapchat) session.

### Weak Points / Repeated Mistakes
1. **Estimation discipline** — cartesian-product counter error (100B vs ~100M) and unchecked scale numbers. **Repeat pattern** (cf. Snapchat media-size estimate). Fix: estimate the *active TTL'd working set*; sanity-check by dividing.
2. **Naming tools instead of mechanisms** — "ELK for metrics" (same shape as "because it's fast"). Fix: lead with the mechanism, then name tools.
3. **Terse-by-default** — answers stayed 3–5 words until pushed; volunteer reasoning proactively.

### Senior-Level Gaps
- Anticipating **hot-key / hot-partition** problems proactively.
- Treating **observability** as a first-class design concern.

### Classification: **Hire** (up from Borderline/Hire in the prior session)
Trending up — trade-off communication is genuinely improving. Held back from Strong Hire by estimation errors and tool-name-over-mechanism habit. Both are fixable habits, not knowledge gaps.

### Recommended Next Problem
Target the recurring **capacity-estimation** gap:
- **Distributed Cache (Redis/Memcached)** — precise memory/working-set/eviction estimation; revisits sharding + hot keys (spaced repetition), **or**
- **URL Shortener** — simpler tech, focus purely on estimation discipline and leading with mechanisms.
- Or a focused **capacity-estimation drill** (5 rapid-fire sizing problems).

---

## 14. Areas to Deep-Dive Next

1. **Capacity estimation drills** — active-working-set vs key-space; sanity-checking numbers (top priority).
2. **Hot-key / hot-partition patterns** — write-sharding, local aggregation, negative caching (reinforce today's lessons).
3. **Rate-limiting algorithms compared** — fixed window, sliding-window log, sliding-window counter, token bucket, leaky bucket (when each wins).
4. **Global quota rebalancing** — how distributed token/quota allocation and borrowing actually work (Envoy global rate limiting, cloud API gateways).
5. **Observability patterns** — local aggregation + async flush, push vs pull metrics, high-cardinality handling.
6. **Redis operational depth** — Cluster vs Sentinel, failover semantics, async replication data-loss windows, atomic Lua scripts for check-and-increment.

---

*Generated from HLD coaching session (guided mode). Use §14 to drive follow-up practice.*
