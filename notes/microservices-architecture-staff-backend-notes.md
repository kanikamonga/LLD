# Microservices Architecture — Staff Backend Engineer Interview Notes

## 1. Staff-level summary

Microservices architecture organizes a system as independently deployable services around business capabilities, with explicit APIs or events and clear data ownership. It trades local simplicity for organizational autonomy and independent scaling, while making network failures, consistency, observability, and operations first-class design concerns.

## 2. Mental model

A microservice is not merely a small codebase or a separate deployment. It is a **service boundary with an owned capability, data, operational lifecycle, and contract**.

The architecture aims to:

- Let teams change and deploy capabilities with appropriate independence
- Scale components according to their workloads
- Isolate some failures and technology choices

It does not automatically provide scalability, resilience, or faster delivery. Poor boundaries can create a distributed monolith.

## 3. Why interviewers care

Microservices questions test whether a candidate can balance:

- Domain boundaries and team ownership
- Latency and availability across network calls
- Data consistency and independent storage
- Deployment autonomy and compatibility
- Failure isolation and system-wide observability
- Operational cost versus organizational benefit

A Staff-level answer should first establish the constraints and consider whether a modular monolith is a better starting point.

## 4. Core concepts

### Service boundaries and domain ownership

Split around cohesive business capabilities and bounded contexts, not merely technical layers or database tables. A service should own its business rules and data lifecycle.

Strong boundaries reduce coordinated changes. If one user-visible operation requires frequent synchronous calls across many services, the decomposition may be wrong or the workflow may need redesign.

### Independent data ownership

Each service should be the authority for its data. Other services use an API or events rather than directly writing its tables.

This reduces schema coupling but means cross-service queries and transactions require explicit design, such as read models, workflows, or data replication.

### Communication

**Synchronous request/response** (for example, HTTP or RPC) is useful when the caller needs an immediate answer. It couples availability and latency to downstream services.

**Asynchronous messaging** is useful for decoupling work, fan-out, and eventual processing. It introduces delivery, ordering, replay, and idempotency concerns.

### API and event contracts

Contracts must evolve compatibly. Use explicit versioning or additive evolution practices, schema validation, and consumer-aware rollout. An event is a statement of a business fact; avoid treating it as a command to every consumer unless that is the intended contract.

### Data consistency and workflows

A distributed transaction across independently owned services is rarely a good default. Use a saga or explicit workflow for multi-step business processes, with compensating actions where appropriate. Compensation is a new business action, not a perfect rollback.

The transactional outbox pattern can atomically persist local state and an event record, then publish asynchronously. Consumers still need idempotency.

### Resilience and isolation

Timeouts, bounded retries, circuit breakers, bulkheads, rate limits, and load shedding help prevent local failures from cascading. Retries need backoff and jitter, and should be safe for the operation.

## 5. Production use cases

| Pattern | When useful | When not useful / common mistake |
|---|---|---|
| Separate service by business capability | Different domain ownership, scaling, or release needs | Splitting solely by code size or technical layer |
| Synchronous service API | Immediate response is required and dependency budget is acceptable | Deep call chains on a latency-critical request |
| Event-driven integration | Independent consumers, asynchronous workflows, replay | Using events without ownership, schema, or delivery strategy |
| Saga/workflow | Business process spans service-owned data | Pretending compensation restores the exact previous world |
| Read model / CQRS-style projection | Query needs combine independently owned data | Introducing projection complexity without query or scale need |
| API gateway | Cross-cutting client concerns and routing | Turning the gateway into a business-logic bottleneck |

## 6. Architecture and design considerations

- Start with business capabilities, domain language, and change boundaries.
- Define service ownership, API/event contracts, SLOs, and operational responsibility.
- Avoid synchronous call chains; budgets for timeouts and tail latency must account for all dependencies.
- Use deadlines propagated across calls and distinguish retryable from non-retryable errors.
- Ensure retries are bounded, use backoff/jitter, and do not multiply load during an outage.
- Design idempotency for commands and event consumers, especially around timeouts and redelivery.
- Use outbox/inbox or workflow patterns where local state and message publication must be coordinated.
- Plan schema and API evolution for mixed-version deployments.
- Secure service-to-service identity, authorization, secrets, and transport.
- Preserve trace context across HTTP and message boundaries.
- Decide discovery, routing, configuration, deployment, and graceful shutdown consistently.
- Consider data retention, deletion obligations, backups, and restore testing for each service's store.
- Create a migration path from the existing architecture; avoid a high-risk all-at-once rewrite.

## 7. Trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| Microservices vs modular monolith | Independent deployment and scaling | Network, platform, and operational complexity | Boundaries and team autonomy justify the cost |
| Sync calls vs events | Direct response and simpler request flow | Availability and latency coupling | Caller needs an immediate result |
| Events vs sync calls | Decoupling, fan-out, replay | Eventual consistency and more complex debugging | Work can be asynchronous or multiple consumers need facts |
| Database per service | Clear ownership and independent evolution | Cross-service queries and transactions become harder | Domain boundaries are stable and ownership matters |
| Saga vs distributed transaction | Looser coupling and local transactions | Compensation, retries, and intermediate states | Long-running business workflows across services |
| API gateway | Centralized edge concerns and client abstraction | Extra hop and potential bottleneck | Multiple clients need consistent edge behavior |
| More services | Smaller ownership units and independent scaling | More deployments, alerts, networking, and on-call load | Team and domain boundaries support genuine autonomy |

## 8. Failure modes and mitigations

| Failure mode | Symptom | Root cause | Mitigation |
|---|---|---|---|
| Cascading failure | Many services fail after one dependency slows | Unbounded waits/retries and shared resource exhaustion | Timeouts, bulkheads, bounded retries, load shedding |
| Distributed monolith | Small changes require coordinated releases | Tight runtime and contract coupling | Revisit boundaries, reduce synchronous dependencies, evolve contracts |
| Duplicate side effects | Repeated charge, email, or state transition | At-least-once delivery or retry after ambiguous timeout | Idempotency keys, deduplication, state-machine guards |
| Lost event/state mismatch | Database update succeeds but event is absent, or vice versa | Dual write across database and broker | Transactional outbox, reconciliation, alerting |
| Stale read model | UI or query shows old data | Asynchronous projection lag | Define freshness SLO, expose status, rebuild/replay projections |
| Retry storm | Load increases during dependency outage | Aggressive or synchronized retries | Backoff, jitter, retry budgets, circuit breaking |
| Contract break | Consumers fail after producer deployment | Incompatible schema/API change | Compatibility rules, staged rollout, consumer testing |
| Hard-to-debug request | Failure cannot be traced across services | Missing correlation and context propagation | Distributed tracing, structured logs, trace/event identifiers |
| Data ownership erosion | Services share tables or bypass APIs | Convenience-driven coupling | Enforce ownership and migrate via controlled interfaces |

## 9. Observability and operations

Monitor both service health and end-to-end outcomes:

- Golden signals per service: latency, traffic, errors, and saturation
- SLOs and error budgets for critical user journeys
- Dependency latency, timeout, retry, and circuit-breaker metrics
- Queue depth, consumer lag, event age, and dead-letter volume
- Workflow completion time and stuck/inconsistent saga states
- Deployment health, rollback rate, and version skew
- Database connection pools, query latency, replication, and storage capacity

Use trace context across synchronous and asynchronous boundaries. Logs should include safe correlation identifiers, not secrets or sensitive payloads. Define runbooks and ownership for each critical dependency and workflow.

## 10. Staff-level talking points

> I would not start by choosing a number of services. I would map business capabilities, ownership, change frequency, scale, and reliability requirements, then choose boundaries that minimize coordinated change.

> Every network call adds latency and a failure mode. I would keep critical synchronous paths shallow, define an end-to-end deadline, and use asynchronous workflows where the caller does not need immediate completion.

> With independent data ownership, consistency becomes explicit. I would identify which invariants must be immediate and which can be eventual, then choose local transactions, idempotent consumers, an outbox, or a saga accordingly.

> I would measure whether the architecture improves team and product outcomes. If independent deployment is not real and every change requires cross-service coordination, the system may be a distributed monolith.

## 11. Common interview questions

### How do you identify microservice boundaries?

**Tests:** Domain modeling and organizational reasoning.

**Answer outline:** Identify business capabilities and bounded contexts, data ownership, change patterns, team ownership, and reliability/scaling needs. Validate that boundaries reduce coordination rather than merely creating more deployments.

### How do you handle a workflow that spans multiple services?

**Tests:** Distributed consistency.

**Answer outline:** Identify the business invariants and acceptable intermediate states. Use a saga or workflow with idempotent steps, timeouts, retries, compensation, and observability. Use an outbox for reliable publication from a local transaction.

### How do you prevent cascading failures?

**Tests:** Resilience under overload and dependency failure.

**Answer outline:** Timeouts/deadlines, bounded retries with backoff and jitter, circuit breakers, bulkheads, rate limits, load shedding, and capacity isolation. Ensure fallback behavior is semantically safe.

### When would you not use microservices?

**Tests:** Ability to reason beyond trends.

**Answer outline:** When the domain and team are small, boundaries are unstable, independent deployment is not needed, or operational maturity is insufficient. A modular monolith can preserve clear boundaries at lower operational cost.

### How do you evolve events without breaking consumers?

**Tests:** Contract and rollout discipline.

**Answer outline:** Prefer compatible additive changes, validate schemas, deploy consumers/producers in a safe order, account for old versions and replay, and monitor consumer failures.

## 12. Mistakes to avoid

- Choosing microservices because they are fashionable or assuming they automatically scale.
- Splitting by technical layers rather than business capabilities.
- Sharing databases while claiming independent ownership.
- Treating retries as harmless or retrying non-idempotent operations blindly.
- Assuming an event broker provides exactly-once business effects.
- Using a saga without defining intermediate states, compensation, and recovery.
- Ignoring service ownership, on-call, observability, and platform costs.
- Requiring global consistency when the business invariant does not need it.

## 13. Quick revision

- A service boundary should reflect capability, ownership, and change—not just code size.
- Prefer clear data ownership; communicate through contracts.
- Sync calls couple latency and availability; events add decoupling and eventual consistency.
- Keep critical request paths shallow and propagate deadlines.
- Use bounded retries with backoff/jitter and idempotent operations.
- Outbox addresses local database-plus-event dual writes; consumers still need safe processing.
- Sagas coordinate business workflows; compensation is not a perfect rollback.
- Evolve APIs/events compatibly for mixed-version deployments.
- Measure end-to-end SLOs, not only individual service health.
- A modular monolith may be the right choice when autonomy does not justify distributed-system cost.
