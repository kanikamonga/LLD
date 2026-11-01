# Preventing Cascading Service Failures — Staff Backend Engineer Notes

## 1. Summary and mental model

Contain a failed dependency by bounding how much time, concurrency, and retry load callers spend on it. Preserve healthy capabilities through isolation and semantically safe degradation rather than letting blocked calls exhaust the whole system.

```text
Dependency slows
  → caller waits longer
  → workers/connections accumulate
  → queues and timeouts grow
  → retries increase load
  → upstream services also fail
```

## 2. Why interviewers ask

The question tests whether you understand failure propagation through shared resources. A circuit breaker alone is not sufficient: containment needs deadlines, bounded concurrency, retry control, and correct product behavior when required data is unavailable.

## 3. Core mechanisms

| Mechanism | Role | Important limitation |
|---|---|---|
| Deadlines/timeouts | Bound elapsed time spent waiting | Configure connection, acquisition, and operation waits; propagate remaining budget |
| Circuit breaker | Stop repeated calls to an unhealthy dependency and probe recovery | Needs correct thresholds; does not replace timeouts or bulkheads |
| Bulkhead | Limit resources consumed by a dependency or workload | Pools/semaphores must be sized against real capacity |
| Bounded retries | Recover from selected transient failures | Unsafe retries can duplicate side effects or amplify outages |
| Backoff and jitter | Spread retry attempts | Still needs attempt/time limits and a retry budget |
| Load shedding | Reject excess work before resources are exhausted | Return an explicit overload response, not false success |
| Safe degradation | Keep nonessential features available | Must preserve business invariants and freshness requirements |
| Asynchronous isolation | Decouple work that need not complete in the request | Adds backlog, delivery, idempotency, and retention concerns |

## 4. Incident response

1. Identify the failing dependency and affected journeys using traces, errors, latency, and saturation metrics.
2. Stop amplification: disable optional calls, constrain retries, shed excess work, or activate tested circuit-breaker/feature controls.
3. Protect remaining capacity with per-dependency concurrency limits and bounded queues.
4. Degrade only where correct. Recommendations can be omitted; an unavailable payment authorization must not be reported as successful.
5. Restore the dependency or roll back a correlated change; allow controlled recovery probes rather than reopening all traffic at once.
6. Confirm upstream recovery and inspect backlog, duplicate work, and inconsistent states.

Do not introduce untested timeout values or fail-open behavior during an incident without considering the business impact.

## 5. Design for containment

- Give each request an end-to-end budget; dependent calls must fit within the remaining time. Cancellation should release resources where supported.
- Bound each dependency's concurrency. A small number of long-running requests can exhaust shared pools even at low traffic.
- Retry only errors considered transient and only when the operation is safe to repeat. Use idempotency keys and bounded deduplication policies for side-effecting operations.
- Avoid retries at every layer. Three attempts at each of three call layers can generate up to 27 deepest-layer attempts for one request.
- Separate essential and optional dependencies, foreground/background work, and tenants where isolation is necessary.
- Bound asynchronous queues and define retention, replay, poison-message handling, and backlog recovery. A broker relocates pressure; it does not eliminate it.
- Separate liveness from readiness. Restarting every healthy process because a shared dependency is down can create a restart storm.
- Test dependency slowness, errors, dropped responses, and recovery through controlled fault injection.
- Define cross-team SLOs, escalation ownership, and runbooks.

## 6. Trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| Short timeout | Releases capacity quickly | May reject legitimately slow operations | Budget follows measured latency and business deadline |
| Circuit breaker | Fast failure during sustained outage | Poor tuning can unnecessarily deny useful traffic | Repeated dependency failures need suppression and controlled probing |
| Bulkhead | Isolates saturation | Reserved capacity may be underused; adds tuning | Failure of one workload must not consume all shared resources |
| Stale cached response | Keeps some reads available | Staleness may violate correctness | Business permits bounded stale data and clients know its status |
| Queue asynchronous work | Decouples request path | Delays completion and adds operational backlog | Immediate completion is unnecessary |
| Load shedding | Preserves service for admitted work | Some requests fail explicitly | Overload threatens overall availability |

## 7. Failure modes and mitigations

| Symptom | Cause | Mitigation |
|---|---|---|
| All workers blocked | No timeout or shared pool consumed by slow dependency | Bound waits; per-dependency bulkheads |
| Load spikes during outage | Retry multiplication or synchronized retries | Retry budget, backoff/jitter, fewer retrying layers |
| False successful response | Unconditional fallback on required dependency failure | Fail explicitly or return a real pending state |
| Repeated collapse during recovery | Circuit closes too aggressively; backlog floods dependency | Limited probes and controlled backlog drain |
| Queue grows without limit | Producers outrun consumers; no admission control | Bounded backlog, backpressure, retention and overload policy |
| Duplicate business effects | Retry after ambiguous success/timeout | Idempotency, deduplication, reconciliation |

## 8. Observability and operations

Monitor dependency latency/error rates, timeout counts, retry ratio, breaker transitions, in-flight limits, queue depth, rejections, and fallback usage. Connect them to user-journey SLOs and error-budget burn.

For asynchronous paths, track event age, backlog growth, dead-letter counts, and recovery throughput. Alert when the system survives technically but business completion is delayed beyond its promised SLO.

## 9. Interview-ready answer

> I prevent a slow or failed dependency from consuming unlimited caller resources. I use propagated deadlines, per-dependency concurrency limits, circuit breakers, and bounded retries with backoff and jitter. I shed load before queues become unbounded and degrade optional functionality only when business semantics permit it. For required operations, I fail explicitly rather than fabricate success. I then validate failure and recovery with controlled tests and monitor retries, saturation, fallback usage, and end-to-end SLOs.

## 10. Follow-ups and mistakes

- **Is a circuit breaker enough?** No: an individual call still needs a timeout, and callers need capacity isolation.
- **Should every error be retried?** No: classify transient errors and operation safety, and respect the remaining deadline.
- **Can we return cached data?** Only when permitted by consistency/freshness requirements and clearly represented.
- Avoid using autoscaling as a substitute for protecting a failing shared dependency.
- Avoid hiding failures with empty/default success responses.

## 11. Quick revision

- Bound time, concurrency, queue size, and retry load.
- Breakers suppress failed calls; bulkheads isolate capacity.
- Retries need idempotency, backoff/jitter, and a budget.
- Degrade optional features; preserve correctness for required ones.
- Async systems still need backpressure and recovery planning.
- Test recovery, not just initial failure.
