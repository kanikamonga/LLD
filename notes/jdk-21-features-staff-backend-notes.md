# JDK 21 Features — Staff Backend Engineer Interview Notes

## 1. Staff-level summary

JDK 21 improves blocking-I/O concurrency with finalized virtual threads, makes data-oriented code clearer through record patterns and pattern-matching switch, adds uniform ordered-collection APIs, and introduces Generational ZGC. It also includes preview and incubator features that must not be confused with stable APIs.

Released on September 19, 2023, JDK 21 is an LTS release for many vendors. Support duration and licensing depend on the distribution and vendor.

## 2. Mental model and release status

Separate three categories:

- **Finalized features:** Available without preview flags.
- **Preview features:** Complete enough for evaluation, but may change or disappear. They require preview enablement and matching release tooling.
- **Incubator APIs:** Experimental APIs in separate modules; explicitly add the required module.

These are the feature statuses **in JDK 21**, not claims about their status in later releases.

| Feature | JEP | JDK 21 status |
|---|---|---|
| Virtual Threads | 444 | Final |
| Record Patterns | 440 | Final |
| Pattern Matching for switch | 441 | Final |
| Sequenced Collections | 431 | Final |
| Generational ZGC | 439 | Delivered; opt-in GC mode |
| Key Encapsulation Mechanism API | 452 | Final |
| String Templates | 430 | Preview |
| Foreign Function & Memory API | 442 | Third preview |
| Unnamed Patterns and Variables | 443 | Preview |
| Unnamed Classes and Instance Main Methods | 445 | Preview |
| Scoped Values | 446 | Preview |
| Structured Concurrency | 453 | Preview |
| Vector API | 448 | Sixth incubator |
| Prepare to Disallow Dynamic Loading of Agents | 451 | Operational change; warnings |
| Deprecate Windows 32-bit x86 Port for Removal | 449 | Port deprecation |

## 3. Why interviewers care

The important distinction is not memorizing JEP numbers. Explain which workload benefits, which features are stable, and what must change operationally. Virtual threads require capacity controls; a GC change requires measurement; preview APIs require an explicit compatibility policy.

## 4. Major finalized features

### Virtual threads — JEP 444

Virtual threads are lightweight JVM-managed threads scheduled on platform carrier threads. Many supported blocking operations let a virtual thread unmount from its carrier so another virtual thread can run.

They support familiar sequential, blocking code at much higher I/O concurrency without requiring a platform thread for every waiting task.

```java
import java.util.concurrent.Executors;

class VirtualThreadExample {
    public static void main(String[] args) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> {
                Thread.sleep(100); // Represents waiting for an I/O operation.
                return "completed";
            });
            System.out.println(result.get());
        }
    }
}
```

**Use cases:** Blocking HTTP handlers, database-backed services, and independent I/O tasks where platform-thread count limits concurrency.

**Cautions:**

- They do not speed up CPU-bound computation or increase database connection capacity.
- The per-task executor does not impose a useful downstream concurrency limit by itself. Use admission controls, semaphores, connection limits, deadlines, and cancellation.
- Do not pool virtual threads merely to reuse them; bound scarce resources separately.
- In JDK 21, blocking while holding a `synchronized` monitor can pin a virtual thread to its carrier; native/foreign calls can also cause pinning. Investigate long/frequent pinning rather than mechanically replacing every synchronized block.
- Large per-thread `ThreadLocal` caches can become costly at high virtual-thread counts.
- This behavior is release-specific; do not assume later runtime improvements are present in JDK 21.

### Record patterns — JEP 440

Record patterns deconstruct record values into their components, avoiding repetitive accessor calls and enabling nested matching.

```java
record Point(int x, int y) {}
record Segment(Point start, Point end) {}

static int horizontalDistance(Object value) {
    if (value instanceof Segment(Point(int x1, int y1),
                                 Point(int x2, int y2))) {
        return Math.abs(x2 - x1);
    }
    throw new IllegalArgumentException("Expected Segment");
}
```

**Use cases:** Processing immutable domain values, ASTs, commands, and event payloads. Records themselves were finalized before JDK 21; record patterns are the new feature here.

### Pattern matching for switch — JEP 441

Switch can select branches by type patterns, use `when` guards, handle `null` explicitly, and check exhaustiveness for suitable sealed hierarchies.

```java
sealed interface PaymentResult permits Approved, Declined {}
record Approved(String transactionId) implements PaymentResult {}
record Declined(String reason) implements PaymentResult {}

static String describe(PaymentResult result) {
    return switch (result) {
        case null -> "No result";
        case Approved(var id) -> "Approved: " + id;
        case Declined(var reason) when reason.isBlank() ->
            "Declined without explanation";
        case Declined(var reason) -> "Declined: " + reason;
    };
}
```

**Benefits:** Less casting and clearer branching over closed result types. This does not mean every polymorphic operation belongs in a switch; use methods/strategies where behavior should vary through extensible implementations.

### Sequenced collections — JEP 431

Adds a consistent API for collections with a defined encounter order:

- `SequencedCollection`
- `SequencedSet`
- `SequencedMap`

Common operations include first/last access and reverse-order views.

```java
var names = new java.util.ArrayList<>(
    java.util.List.of("Asha", "Ben", "Chen")
);

System.out.println(names.getFirst()); // Asha
System.out.println(names.getLast());  // Chen
System.out.println(names.reversed()); // [Chen, Ben, Asha]
```

**Benefits:** Removes inconsistencies between ordered collection APIs. `reversed()` is a view, not necessarily an independent copy. Supported mutations depend on the underlying collection; empty first/last access can throw.

### Generational ZGC — JEP 439

Separates young and old objects so the collector can reclaim short-lived objects more efficiently while preserving ZGC's low-pause goals.

In JDK 21, enable it explicitly:

```bash
java -XX:+UseZGC -XX:+ZGenerational -jar app.jar
```

**Use cases:** Latency-sensitive services where GC pauses matter and adequate CPU/memory headroom exists.

**Cautions:** It is not the default GC in JDK 21; `UseZGC` alone selects non-generational ZGC in this release. Benchmark against the current collector using representative allocations and container limits. Low GC pause goals are not an end-to-end application latency guarantee.

### Key Encapsulation Mechanism API — JEP 452

Provides the `javax.crypto.KEM` API for deriving shared secret keys using encapsulation/decapsulation. This supplies a standard abstraction for protocols and algorithms that use KEMs.

**Use cases:** Cryptographic libraries and protocol implementations establishing shared keys.

Use reviewed protocols and supported providers. The API does not automatically make existing applications post-quantum secure or replace TLS integration.

## 5. Preview features

### Structured concurrency — JEP 453

Treats related subtasks as one unit with a bounded lifetime. `StructuredTaskScope` supports coordinating completion, failure, and cancellation.

**Use case:** A request fetches account and inventory data concurrently and requires both results. A failure policy can cancel sibling work rather than leaving tasks running beyond their useful lifetime.

It complements virtual threads: virtual threads supply cheap execution; structured concurrency supplies task-lifecycle coordination. Cancellation still depends on tasks and libraries responding appropriately.

### Scoped values — JEP 446

Allows immutable bindings to be shared within a bounded execution scope. It is an alternative to some `ThreadLocal` use cases, particularly request context in structured task trees.

**Use cases:** Trace context, tenant identity, or immutable request metadata.

An immutable binding does not make the referenced object deeply immutable; context propagation across arbitrary executor boundaries is not automatic.

### String templates — JEP 430

Combines embedded expressions with a template processor.

```java
// JDK 21 preview syntax; not portable to arbitrary later Java releases.
String name = "Asha";
String message = STR."Hello, \{name}!";
```

Custom processors can validate or transform embedded values. Plain interpolation is not automatically safe SQL construction; use parameterized SQL.

**Compatibility warning:** String templates were previewed in JDK 21, not standardized by it. Their preview did not continue unchanged into later releases; do not present this syntax as a stable modern Java API.

### Foreign Function & Memory API — JEP 442

Provides APIs for accessing off-heap memory and calling native code, with concepts such as memory segments, arenas, and linkers.

**Use cases:** Native-library integration and specialized off-heap processing without traditional JNI boilerplate.

In JDK 21 this is the third preview. Native boundaries still require correct layouts, lifetimes, ABI assumptions, and appropriate native-access configuration.

### Unnamed patterns and variables — JEP 443

Allows `_` to mark intentionally unused bindings, such as a catch variable or an unneeded record component.

**Benefits:** Signals intent and reduces irrelevant names. Requires preview enablement in JDK 21.

### Unnamed classes and instance main methods — JEP 445

Reduces ceremony for small programs and learning:

```java
// JDK 21 preview source file.
void main() {
    System.out.println("Hello");
}
```

It is a simpler entry point, not a requirement to redesign normal backend application structure.

### Running preview code

For a complete source file using JDK 21 preview features:

```bash
javac --enable-preview --release 21 Example.java
java --enable-preview Example
```

Use JDK 21 tooling for JDK 21 preview code. Preview class files and APIs are tied to the release; enabling preview on a later JDK does not guarantee compatibility.

## 6. Other platform changes

### Vector API — JEP 448, sixth incubator

Exposes vector computations that can map to SIMD instructions on supported hardware.

**Use cases:** Numerical processing, image processing, and other data-parallel kernels. It does not automatically optimize arbitrary business logic. Benchmark with relevant hardware and enable the incubator module, typically `--add-modules jdk.incubator.vector`.

### Dynamic agent loading warnings — JEP 451

JDK 21 warns when agents are loaded dynamically, preparing for a future change in defaults. This matters for profilers, instrumentation libraries, and test tools that attach agents at runtime.

Dynamic agent loading is **not generally disabled by default in JDK 21**. Audit tooling and use documented startup-agent configuration where appropriate rather than suppressing warnings without understanding them.

### Windows 32-bit x86 port deprecation — JEP 449

Deprecates this port for future removal. Audit affected build and deployment targets; deprecation is not the same as removal in JDK 21.

## 7. Adoption trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| Virtual threads for blocking I/O | More concurrent waiting tasks with straightforward code | Downstream overload, pinning, and context costs | Platform-thread limits constrain an I/O-heavy workload |
| Pattern matching for domain variants | Clearer, exhaustive branching | Centralized switches can conflict with open-ended extension | Closed result/event hierarchies |
| Generational ZGC | Potentially lower pause/collection overhead for suitable workloads | CPU/memory trade-offs; measurement required | GC is a demonstrated latency concern |
| Preview APIs | Early access to useful capabilities | Source/API churn and release-coupled builds | Controlled experiments with explicit migration budget |
| Vector API | Efficient SIMD kernels | Incubator dependency and hardware sensitivity | Measured numerical/data-parallel bottleneck |

## 8. Failure modes and mitigations

| Symptom | Cause | Mitigation | Interview signal |
|---|---|---|---|
| Database overloaded after virtual-thread rollout | Concurrency no longer limited by worker count | Admission limits, pool bounds, deadlines | Cheap threads do not mean unlimited capacity |
| Virtual-thread scalability below expectations | Frequent long pinning or CPU saturation | Use JFR/pinning diagnostics; profile critical regions | Know JDK 21-specific runtime behavior |
| Build/runtime rejects preview code | Wrong JDK or preview flag configuration | Align compiler, tests, runtime, and release | Preview is not a stable language contract |
| Latency regresses after GC change | Workload/headroom differs from assumptions | Compare representative benchmarks and canaries | Collector choice is empirical |
| Instrumentation warns or fails after upgrade | Dynamic attachment/tooling assumptions | Upgrade agents and check supported startup configuration | Runtime migrations include tooling |

## 9. Observability and rollout

- Check framework, driver, build-plugin, agent, and container compatibility.
- Separate the JDK runtime upgrade from major concurrency or GC changes so regressions are attributable.
- Canary under representative load; keep a rollback path.
- Compare p95/p99 latency, throughput, errors, CPU, RSS/heap, allocation, GC pauses, and downstream pool waits.
- For virtual threads, inspect supported JFR events/thread diagnostics and actual downstream concurrency.
- Alert on SLO/error-budget degradation, pool acquisition timeouts, pinning trends where available, and memory pressure.
- Record preview use explicitly and keep it out of shared stable contracts unless the ownership and compatibility costs are accepted.

## 10. Interview-ready explanation

> The biggest backend change in Java 21 is finalized virtual threads: they improve scalability for blocking I/O while preserving familiar sequential code. They do not speed up CPU work or remove database limits. Record patterns, pattern-matching switch, and sequenced collections improve everyday code clarity. Generational ZGC is an opt-in runtime improvement worth measuring for latency-sensitive workloads. I would distinguish these from preview features such as structured concurrency and scoped values, and validate both dependency compatibility and operational behavior during adoption.

## 11. Common interview questions

### Do virtual threads replace reactive programming?

**Tests:** Architectural judgment.

**Answer outline:** They offer an alternative for blocking-I/O concurrency, not universal replacement. Consider existing framework architecture, end-to-end backpressure, workload, library support, and migration cost.

**Trap:** Cheap waiting does not provide unlimited admission capacity.

### Are structured concurrency and scoped values stable in JDK 21?

**Tests:** Release accuracy.

**Answer outline:** No, both are preview in JDK 21. Virtual threads are final.

**Trap:** A feature being stable in some later release does not change its JDK 21 status.

### What is the difference between records and record patterns?

**Tests:** Language understanding.

**Answer outline:** Records declare transparent data carriers and predate JDK 21. Record patterns deconstruct record values during matching and became final in JDK 21.

### Does JDK 21 use Generational ZGC by default?

**Tests:** Runtime accuracy.

**Answer outline:** No. Select ZGC and generational mode explicitly in JDK 21; evaluate against the current collector.

## 12. Mistakes to avoid

- Calling all Java 21 features production-stable.
- Claiming virtual threads make CPU-bound work faster.
- Pooling virtual threads instead of bounding scarce resources.
- Applying later releases' pinning behavior or GC defaults to JDK 21.
- Claiming records or sealed classes first appeared in JDK 21.
- Treating string-template preview syntax as permanently supported.
- Claiming dynamic agent loading was disabled in JDK 21.
- Promising automatic performance gains without representative measurement.

## 13. Quick revision

- **Final:** Virtual threads, record patterns, pattern-matching switch, sequenced collections, KEM API.
- **GC:** Generational ZGC delivered as an opt-in mode in JDK 21.
- **Preview:** Structured concurrency, scoped values, string templates, FFM, unnamed patterns/variables, simpler main methods.
- **Incubator:** Vector API.
- **Operations:** Dynamic-agent warnings; Windows x86 32-bit port deprecation.
- Adopt stable language improvements freely within compatibility constraints; measure concurrency and GC changes.
- Maintain explicit capacity limits, preview policy, observability, and rollback.

## Sources

- [OpenJDK JDK 21 release and complete JEP list](https://openjdk.org/projects/jdk/21/)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 439: Generational ZGC](https://openjdk.org/jeps/439)
- Feature-specific JEPs are available at `https://openjdk.org/jeps/<number>` using the numbers in the release-status table.
