# Java Multithreading — Staff Backend Engineer Interview Notes

## 1. Staff-level summary

Java multithreading lets a process make progress on multiple tasks concurrently, and in parallel when hardware permits. Correctness depends on controlling shared mutable state, establishing visibility and ordering guarantees, and bounding resource use—not merely creating more threads.

**Interpretation:** These notes focus on Java concurrency for backend services.

## 2. Mental model

Separate three concerns:

1. **Execution:** Which thread runs the work?
2. **Coordination:** How do threads communicate or wait?
3. **Correctness:** What guarantees prevent races, stale reads, and invalid state?

Concurrency is about overlapping work; parallelism is simultaneous execution. A program may be concurrent without running on multiple cores.

## 3. Why interviewers care

Concurrency affects:

- Request throughput and tail latency
- Data correctness under load
- Resource exhaustion and overload behavior
- Deadlocks and cascading failures
- Shutdown and deployment reliability
- Whether capacity scales with cores or stalls on contention

A strong answer identifies the shared state, synchronization invariant, lifecycle, and overload policy—not only the API used.

## 4. Core concepts

### Threads and executors

`Thread` represents an execution path. In server applications, prefer managed execution with `ExecutorService` or framework-managed pools rather than creating an unbounded thread per task.

An executor separates task submission from thread management. Its queue, maximum concurrency, rejection policy, and shutdown behavior are part of the system's capacity design.

### Shared mutable state and race conditions

A race occurs when correctness depends on timing between concurrent accesses, typically when at least one access mutates shared state and there is no adequate coordination.

Prefer immutable values, confinement (one thread owns the state), or message passing. Synchronization is needed when shared mutation is unavoidable.

### Java Memory Model and happens-before

The Java Memory Model defines when writes by one thread become visible to another. A **happens-before** relationship provides visibility and ordering guarantees.

Examples include:

- Unlocking a monitor happens-before a later lock of that same monitor.
- A write to a `volatile` field happens-before a later read of that field.
- Actions before submitting a task to an executor happen-before that task begins execution under the executor contract.

Without a safe publication mechanism, another thread may observe stale or partially initialized state.

### `synchronized` and locks

`synchronized` provides mutual exclusion and visibility through monitor acquire/release semantics. `Lock` implementations can offer features such as timed acquisition, interruptible acquisition, or multiple conditions, but require disciplined release, normally in `finally`.

Use the narrowest lock scope that preserves the invariant. Excessive lock scope reduces concurrency; insufficient scope breaks correctness.

### `volatile`

`volatile` provides visibility and ordering for reads and writes of a variable. It does **not** make compound operations such as `count++` atomic.

Use it for simple state publication or flags when the invariant does not require an atomic compound update.

### Atomic variables

Classes such as `AtomicInteger` use atomic operations for individual values and compare-and-set patterns. They are useful for simple counters and state transitions, but do not automatically make a multi-field invariant atomic.

### Concurrent collections

Concurrent maps, queues, and other concurrent collections provide defined thread-safe operations. Compound workflows may still need atomic methods or external coordination; two individually thread-safe calls do not necessarily make the sequence atomic.

### Futures and asynchronous composition

`Future` represents a result available later; `CompletableFuture` supports composition and callbacks. Async code needs explicit decisions about executor selection, timeout, cancellation, exception handling, and context propagation.

### Virtual threads

Virtual threads (available in modern Java releases) make large numbers of blocking tasks cheaper to represent. They do not make CPU-bound work faster, remove downstream capacity limits, or make shared state safe. Verify runtime version and library behavior before adopting them.

## 5. Common backend use cases

| Use case | Useful approach | Watch out for |
|---|---|---|
| Serving concurrent requests | Framework-managed request execution and bounded dependencies | Thread-pool starvation and blocking calls |
| Parallel independent I/O | Bounded executor or virtual threads where supported | Downstream limits, timeouts, cancellation |
| CPU-heavy computation | Pool sized around available CPU and workload | Oversubscription and contention |
| Shared counters/state | Atomic type or lock around a clear invariant | Atomicity across multiple fields |
| Producer-consumer pipeline | Bounded blocking queue or messaging system | Backpressure and shutdown signaling |
| Scheduled/background tasks | Managed scheduler/executor | Duplicate execution, overlap, and lifecycle |

## 6. Design considerations

- Define the invariant first: what must be true before and after concurrent operations?
- Bound concurrency and queues. An unbounded queue can turn overload into memory exhaustion and extreme latency.
- Choose pool size based on whether work is CPU-bound, blocking I/O, or mixed; measure rather than relying on a universal formula.
- Set deadlines and timeouts for blocking operations; cancellation must propagate meaningfully.
- Avoid holding locks while performing network or disk I/O.
- Define interruption and shutdown behavior. Restore interrupt status or propagate interruption rather than swallowing it.
- Avoid nested locks where possible; if needed, acquire them in a consistent global order.
- Consider context propagation (trace IDs, security context, request metadata) across executor boundaries.
- Use concurrent data structures for supported operations, but reason about the entire multi-step invariant.
- Measure lock contention, queue depth, active threads, and task wait time.

## 7. Trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| Immutable or thread-confined state | Simplifies correctness | Can require copying or ownership design | Default for most application data |
| `synchronized` | Simple mutual exclusion and visibility | Contention; monitor scope can be broad | Small, clear critical sections |
| Explicit `Lock` | Timed/interruptible locking and conditions | More complex lifecycle and misuse risk | Need capabilities beyond intrinsic monitors |
| Atomic variable | Low-overhead atomic update for one value | Does not protect multi-value invariants | Counters or simple CAS state |
| Bounded thread pool and queue | Protects the process and exposes overload | Rejections need deliberate handling | Most production task execution |
| Virtual threads | Cheap blocking-task concurrency | Does not solve capacity or CPU limits; compatibility concerns | Blocking workloads on supported modern Java |
| Parallel execution | Can reduce latency for independent work | Coordination, resource contention, failure aggregation | Work is independent and capacity is available |

## 8. Failure modes and mitigations

| Failure mode | Symptom | Root cause | Mitigation |
|---|---|---|---|
| Data race | Intermittent incorrect state | Unsynchronized shared mutation | Establish ownership, immutability, or synchronization |
| Stale read | Thread sees old value | No visibility/happens-before guarantee | Use synchronization, volatile, or safe publication |
| Deadlock | Threads wait indefinitely | Cyclic lock acquisition or blocking dependency cycle | Reduce lock scope; consistent lock ordering; timeouts where appropriate |
| Starvation | Some tasks make little progress | Unfair contention or saturated pool | Inspect scheduling and pool configuration; isolate workloads |
| Pool exhaustion | Requests queue or time out | Threads blocked on slow downstream work | Bound work, set deadlines, isolate bulkheads, tune capacity |
| Queue growth / OOM | Memory rises under overload | Unbounded work admission | Bounded queues, rejection/backpressure, load shedding |
| Lost update | Concurrent writes overwrite each other | Read-modify-write not atomic | Lock or atomic update around the invariant |
| Shutdown hangs | Process does not terminate | Tasks ignore interruption or wait indefinitely | Cooperative cancellation, bounded waits, graceful shutdown |
| Excessive contention | Throughput plateaus and latency rises | Shared lock or hot atomic state | Reduce shared coordination or partition the state |

## 9. Observability and operations

Monitor:

- Active threads, pool size, queue depth, and rejected tasks
- Task execution time versus queue wait time
- Request latency percentiles and timeout/cancellation rates
- CPU utilization, context switches, memory, and garbage collection
- Lock contention and blocked-thread counts from runtime diagnostics
- Downstream connection-pool saturation and dependency latency

Use thread dumps during hangs or high blocking. Correlate task metrics with request traces. Alert on sustained queue growth and rejection, not just raw thread count.

## 10. Staff-level talking points

> I start with the invariant and ownership model. If state can be immutable or confined, I avoid synchronization; otherwise I choose the smallest coordination primitive that makes the whole invariant atomic.

> I treat executors as capacity controls. Pool size, queue bounds, deadlines, rejection behavior, and shutdown policy determine whether overload is contained or amplified.

> Virtual threads reduce the cost of representing blocking tasks, but they do not remove downstream bottlenecks, CPU limits, or the need for timeouts and backpressure.

## 11. Common interview questions

### What is the difference between `volatile` and `synchronized`?

**Tests:** Visibility versus atomicity.

**Answer outline:** `volatile` provides visibility/order for a variable but not mutual exclusion or atomic compound operations. `synchronized` provides mutual exclusion and visibility on monitor boundaries.

### Is `count++` thread-safe if `count` is volatile?

No. Increment is a read-modify-write sequence. Use an atomic increment or synchronize around the operation.

### How would you size a thread pool?

**Tests:** Workload reasoning.

**Answer outline:** Classify CPU versus blocking work, measure service time and downstream capacity, bound queues, set deadlines, and load test. Avoid treating a formula as a substitute for measurements and overload policy.

### What causes deadlock, and how do you prevent it?

**Tests:** Lock-order reasoning.

**Answer outline:** Deadlock can arise from a cycle of threads holding resources while waiting for one another. Reduce nested locking, acquire locks consistently, keep critical sections small, and inspect thread dumps.

### When are virtual threads useful?

**Tests:** Modern runtime trade-offs.

**Answer outline:** They can make blocking I/O concurrency cheaper on supported runtimes, but do not speed up CPU-bound tasks or increase database/API capacity. Bound downstream concurrency and verify library compatibility.

## 12. Mistakes to avoid

- Equating concurrency with parallelism.
- Assuming `volatile` makes compound updates atomic.
- Assuming a concurrent collection makes a multi-call workflow atomic.
- Creating unbounded threads or queues.
- Holding locks over slow I/O.
- Swallowing interruption or ignoring cancellation.
- Claiming virtual threads eliminate resource limits.
- Increasing pool size without checking downstream capacity and queueing behavior.

## 13. Quick revision

- Correctness begins with the invariant and ownership of mutable state.
- Happens-before establishes visibility and ordering.
- `volatile` is visibility, not compound atomicity.
- `synchronized` provides mutual exclusion and visibility.
- Atomic classes protect individual atomic operations, not arbitrary multi-field state.
- Executors are resource and overload controls.
- Bound pools/queues; define timeout, rejection, cancellation, and shutdown behavior.
- Avoid long lock scopes and inconsistent nested lock ordering.
- Virtual threads help blocking concurrency, not CPU throughput or downstream capacity.
- Observe queue wait, task time, rejections, contention, and latency percentiles.
