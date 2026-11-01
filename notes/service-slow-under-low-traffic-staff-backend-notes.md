# Service Slow Under Low Traffic — Staff Backend Engineer Interview Notes

## 1. Summary and mental model

Low request volume does not rule out saturation. A few requests can occupy every worker or database connection if they wait long enough; background work, CPU throttling, locks, and downstream latency can also make a quiet service slow.

Break latency into queue wait, application execution, dependency time, and response transfer. Find where elapsed time is spent before changing capacity.

## 2. Why interviewers ask

This tests whether you diagnose from evidence rather than assume that every slowdown is caused by high traffic. Distinguish CPU work from waiting, end-to-end latency from local service time, and current request rate from accumulated in-flight work.

## 3. Investigation workflow

1. **Define the scope.** Which endpoint, instance, tenant, and percentile is slow? Compare healthy and affected instances. Check recent deployments, configuration changes, infrastructure events, and cold-start timing. Percentiles are noisy when samples are sparse.
2. **Trace a slow request.** Separate gateway/client time from server time; inspect database calls, remote APIs, DNS/TLS, retries, connection acquisition, and queue wait. Missing trace spans may require targeted instrumentation.
3. **Check actual capacity.** Inspect CPU usage and container throttling, memory/GC, thread pools, database/HTTP connection pools, queue depth, disk I/O, and network behavior.
4. **Inspect execution and waits.** Take several thread dumps seconds apart to identify persistent waits, lock contention, deadlocks, or repeated stacks. Use a bounded JFR recording for CPU, allocation, GC, and supported wait/lock events.
5. **Inspect dependencies.** Check slow queries, query plans, database locks, connection leaks, remote-service latency, and timeout/retry behavior. A stalled dependency can consume local capacity.
6. **Test a hypothesis.** Compare with a healthy instance or replay a representative request safely. Avoid changing several settings simultaneously.
7. **Mitigate and verify.** Roll back a correlated bad change or isolate a faulty instance when justified; then confirm latency, error rate, correctness, and saturation improve under the same request pattern.

## 4. Typical causes and signals

| Cause | Evidence | Corrective approach |
|---|---|---|
| Slow database or remote dependency | Long dependency spans; workers waiting on I/O | Optimize query/lock issue; set deadlines; bound waits and concurrency |
| Pool exhaustion or resource leak | Connection acquisition wait despite low arrivals | Find long-held/leaked connections; fix lifecycle before increasing pool |
| Lock contention/deadlock | Repeated dumps show blocked threads and lock ownership | Fix lock scope/order; avoid slow I/O under shared locks |
| CPU throttling or expensive request | Throttling counters or hot stacks | Fix CPU-heavy path or align CPU limits with measured need |
| GC pressure | Pauses align with latency; allocation or live set grows | Profile allocations; investigate retention if post-GC heap grows |
| Background job | Slow periods coincide with scheduler, I/O, or CPU activity | Isolate job resources and schedule/bound its work |
| Cold path | First request after idle/startup is slow | Measure initialization, connection establishment, and scale-to-zero behavior |
| Hidden retries or long timeouts | Multiple dependency attempts in one request | Propagate deadlines; bound retries and avoid retry multiplication |

## 5. Architecture and capacity considerations

Little's Law relates average in-flight work to throughput and time in a stable system:

```text
average in-flight work = average throughput × average time in system
```

For example, 2 requests/second taking 30 seconds imply about 60 in-flight requests on average if the system is stable. That can saturate a 20-worker pool. If arrivals exceed service capacity, queues grow and this stable-state calculation is not a capacity guarantee.

Separate interactive and background workloads where appropriate. Define admission limits, bounded queues, pool acquisition timeouts, downstream deadlines, and cancellation behavior.

## 6. Trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| Repeated thread dumps | Quick evidence of persistent waits | Snapshots can miss brief activity; collection has impact | Suspected blocking, locks, or deadlock |
| Bounded JFR recording | Correlates runtime activity with slowdown | Overhead depends on settings and events | CPU/GC/lock questions require runtime evidence |
| Increase pool size | May help when spare downstream capacity exists | Can overload dependency and amplify failure | Measurements show pool size, not dependency, is the bottleneck |
| Restart instance | Can restore capacity temporarily | Loses evidence and does not fix cause | Incident containment with diagnostics captured if safe |
| Add replicas | Can spread independent work | Does not fix shared database locks or dependency bottlenecks | Bottleneck is genuinely instance-local and scalable |

## 7. Observability and operations

Track latency distributions with request counts, errors, in-flight work, queue wait, pool acquisition wait, dependency duration, CPU throttling, GC pauses, and scheduled job timing. Correlate traces with deployment and infrastructure events.

A heap dump is not the default latency diagnostic. Use one when there is evidence of retained-heap growth; use threads/JFR/traces to investigate execution and waiting.

## 8. Interview-ready answer

> I first identify whether the slowdown is endpoint-specific, instance-specific, or system-wide, and trace a slow request to find where time is spent. Low traffic does not exclude saturation: a few stuck requests or a background job can exhaust workers or connections. I check pool waits, database locks, downstream latency, CPU throttling, and GC, then use repeated thread dumps or JFR to distinguish waiting from CPU work. I test one hypothesis at a time and verify the fix against latency, errors, correctness, and resource saturation.

## 9. Follow-ups and mistakes

- **Low CPU but high latency:** Look for I/O waits, locks, pool acquisition, queueing, or hidden retries; low CPU alone is not proof of a dependency issue.
- **Only one instance is slow:** Compare configuration, heap/GC, threads, connections, host conditions, and traffic mix.
- Avoid assuming that more threads or replicas will solve a shared dependency bottleneck.
- Do not start with a heap dump unless memory evidence warrants it.

## 10. Quick revision

- Trace elapsed time before tuning resources.
- Low arrivals can coexist with many slow in-flight requests.
- Compare healthy and affected instances.
- Check pools, dependencies, locks, throttling, GC, and background work.
- Repeated thread dumps show persistent waits; JFR supplies runtime context.
- Fix the cause and verify under comparable conditions.
