# GraphQL API Architecture — Staff Backend Engineer Interview Notes

## 1. Staff-level summary

GraphQL is a typed API query language and runtime that lets clients request the data and relationships they need through a schema. It is most valuable when many clients have different data-shaping needs; it is not automatically faster, simpler, or a replacement for every REST endpoint, RPC call, or event stream.

## 2. Mental model

Think of GraphQL as a **client-facing data graph and execution contract**:

```text
Client query → schema validation → resolver execution → data sources → typed response
```

The schema defines types and operations. Resolvers provide field values, often by calling services, databases, or other APIs. Clients select fields and related objects in a query.

GraphQL solves:

- Over-fetching and under-fetching across varied client screens
- Coordinating data from multiple backend capabilities behind one client API
- Strongly typed, discoverable API contracts
- Evolving an API by adding fields while supporting existing clients

GraphQL does not inherently solve:

- Backend service boundaries or data ownership
- N+1 query behavior
- Authorization and object-level access control
- Caching, rate limiting, or query-cost control
- Distributed consistency or transactional workflows
- Poorly designed underlying services

## 3. Why it matters in backend interviews

GraphQL discussions reveal whether a candidate distinguishes **client flexibility** from **backend complexity**. A strong answer considers who controls queries, how resolvers map to owned data, how expensive operations are bounded, and how the API fits with existing REST or event-driven interfaces.

Important dimensions include:

- Client iteration speed and network efficiency
- Query cost, latency, and tail behavior
- Resolver fan-out and N+1 access patterns
- Authorization at field and object boundaries
- Caching and observability
- Schema evolution and ownership across teams
- Whether a gateway/federation layer becomes a central bottleneck

## 4. Core concepts

### Schema and type system

The schema describes object types, fields, relationships, and operations. It acts as a contract that clients can introspect and tooling can use for validation and code generation.

Schema evolution is often additive: add fields or types without breaking existing clients, then deprecate and eventually remove old fields according to usage and compatibility policy.

### Query, mutation, and subscription

- **Query:** Reads data.
- **Mutation:** Requests a state change.
- **Subscription:** Delivers ongoing updates, commonly over a persistent connection.

These are API operation categories, not guarantees about database transaction semantics. A mutation that spans multiple services still needs appropriate consistency and workflow design. Subscriptions add connection lifecycle, fan-out, authorization, and backpressure concerns.

### Resolvers

Resolvers return values for fields. They can call repositories, internal APIs, caches, or domain services. Resolver boundaries are not automatically service boundaries; resolver code should preserve domain ownership rather than becoming a second business-logic layer.

### DataLoader and batching

Nested GraphQL selections can cause an N+1 pattern: resolve a list of parent objects, then issue one child lookup per parent. Request-scoped batching and caching, commonly implemented with a DataLoader pattern, can combine lookups.

Batching reduces round trips but needs correct cache scope, authorization isolation, and limits. A request-global cache must not accidentally share values across users or security contexts.

### Nullability and errors

GraphQL responses can contain both `data` and `errors`. Nullability influences how an error propagates through the response tree. Clients and server teams need a deliberate policy for partial results, error codes, and observability.

### Federation and schema composition

Federation or schema stitching can compose subgraphs owned by different teams. This can support organizational autonomy, but it introduces schema ownership, cross-subgraph calls, distributed failure, composition governance, and performance complexity. It is not a substitute for good service boundaries.

## 5. Strengths

- **Client-shaped responses:** Clients request the fields needed for a view, reducing some over-fetching and under-fetching.
- **Fewer round trips for composite views:** Related data can be fetched through one client-facing operation.
- **Typed and discoverable contract:** Schema tooling enables validation, documentation, generated clients, and IDE support.
- **Client autonomy:** Web, mobile, and partner clients can use different selections without requiring a new endpoint for every view.
- **Incremental schema evolution:** Additive fields can allow clients to migrate independently when deprecation is managed well.
- **Aggregation boundary:** A GraphQL layer can provide a useful backend-for-frontend or API composition layer while underlying services retain data ownership.

## 6. Weaknesses and risks

- **Unbounded query shapes:** A client can request deep, broad, or expensive graphs unless depth, complexity, pagination, and rate limits are enforced.
- **N+1 and resolver fan-out:** A compact query can trigger many backend calls and produce poor tail latency.
- **Caching is less straightforward than conventional REST:** Shared HTTP caches work best with stable resource URLs; GraphQL commonly uses POST and variable query bodies. Persisted queries, normalized client caches, and response caching can help, but require design.
- **Authorization complexity:** Access checks must be consistent across fields and object relationships. Protecting only the root operation can expose nested data.
- **Operational visibility:** A single endpoint carries many operation shapes. Logs and metrics need operation names, safe query identifiers, resolver timing, and cardinality controls.
- **Partial-error semantics:** Clients must handle data with errors, nullable fields, and error propagation correctly.
- **Schema governance:** A shared schema can become contentious or tightly coupled without clear ownership and compatibility processes.
- **Subscriptions increase operational burden:** Persistent connections consume resources and complicate scaling, authorization refresh, reconnect behavior, and delivery guarantees.
- **Gateway can become a bottleneck:** A centralized graph layer may add latency, become a critical dependency, or absorb too much business logic.
- **Not ideal for every client or workload:** Simple resource CRUD, internal low-latency RPC, bulk transfer, or event publication may fit other styles better.

## 7. Comparison with other API styles

| Style | Strengths | Trade-offs | Good fit |
|---|---|---|---|
| **REST** | Resource-oriented URLs, strong HTTP semantics, straightforward caching and observability | Multiple calls or fixed representations can cause over/under-fetching; endpoint versioning can accumulate | Public resource APIs, CRUD, stable resource boundaries, cache-friendly reads |
| **GraphQL** | Flexible typed selections, composite data fetching, client-specific views | Query-cost control, resolver performance, caching, authorization, and schema governance require investment | Multiple clients with varying data needs and graph-shaped relationships |
| **gRPC / RPC** | Efficient typed service-to-service calls, explicit operations, streaming options | Client generation and browser support may be less direct; point-to-point coupling remains | Internal service communication with known callers and latency-sensitive contracts |
| **Event-driven APIs** | Temporal decoupling, fan-out, asynchronous workflows, replay depending on platform | Eventual consistency, delivery semantics, schema evolution, and operations | Business events, background processing, integration, and workflows not requiring immediate response |

These styles can coexist. For example, a GraphQL API can serve web/mobile clients, REST can remain the public resource API, gRPC can connect internal services, and events can carry asynchronous business facts.

## 8. When to advocate for GraphQL

Advocate for GraphQL when:

- Several clients need different projections of the same domain graph.
- Screens or workflows combine data from multiple backend capabilities and minimizing client round trips matters.
- Client teams need to evolve independently against a typed contract.
- There is a clear API composition or backend-for-frontend responsibility.
- The organization can invest in schema governance, query controls, resolver performance, and observability.
- Data access can be served efficiently through batching, purpose-built read models, or bounded downstream calls.

Good examples include complex product detail views, mobile applications with variable network conditions, and dashboards that compose related data with different field requirements.

GraphQL may also be introduced incrementally as a client-facing layer over existing REST services; the underlying services do not need to be rewritten.

## 9. When to advise against GraphQL

Prefer another style—or keep GraphQL out of the design—when:

- The API is a small, stable CRUD surface with predictable resource representations and HTTP caching is important.
- There is one client with fixed needs and little pressure to combine data or evolve projections.
- Internal service calls need a small set of explicit, low-latency operations; gRPC or REST may be clearer.
- The primary integration is asynchronous event publication or consumption.
- The team lacks capacity to enforce query complexity, pagination, authorization, and resolver performance.
- The graph layer would become a thin pass-through that adds another hop without meaningful client or ownership benefits.
- Large binary uploads/downloads or bulk data transfer dominate; dedicated HTTP endpoints or object storage are often more suitable.
- A public API needs simple, predictable resource-level caching and rate limiting, and the proposed GraphQL operations cannot be constrained well.

Avoid framing this as “GraphQL is bad” or “REST is old.” Choose the interaction model that best fits caller needs, system constraints, and operating capability.

## 10. Design and production considerations

- **Query cost controls:** Enforce pagination, maximum depth/complexity, timeouts, rate limits, and where appropriate persisted/allow-listed operations.
- **Resolver efficiency:** Batch reads, avoid serial calls, set bounded concurrency, and measure per-resolver latency and fan-out.
- **Authorization:** Check permissions at the appropriate object/field boundary. Ensure batching and caches respect tenant and user isolation.
- **Data ownership:** Delegate domain rules to owning services. Do not let the graph gateway become an ungoverned shared database layer.
- **Caching:** Choose cache levels deliberately—client normalization, request-scoped DataLoader caching, persisted query caching, resolver/data caching, or HTTP caching where applicable. Define invalidation and tenant isolation.
- **Pagination:** Prefer bounded cursor-based pagination for large or changing collections; avoid returning unbounded lists.
- **Error contract:** Define which failures yield partial data, what clients can retry, and how internal details are redacted.
- **Observability:** Track operation name/hash, total duration, resolver duration, downstream calls, error rate, complexity, and result size. Avoid logging sensitive query variables or high-cardinality raw query text indiscriminately.
- **Schema governance:** Assign ownership, require compatibility checks, monitor deprecated-field usage, and provide a safe removal process.
- **Availability:** Bound downstream timeouts and concurrency; define fallback or partial-response behavior where semantically safe.
- **Subscriptions:** Define authorization renewal, connection limits, reconnect behavior, event ordering, retention, and backpressure.

## 11. Trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| One flexible GraphQL endpoint | Client selects response shape | Query cost and operations are harder to constrain | Diverse clients and data requirements justify platform investment |
| REST resources | Predictable semantics and HTTP caching | Multiple endpoints/calls may be required for composite views | Resource-oriented, cacheable, stable APIs |
| GraphQL gateway over services | Central client composition and schema | Extra hop and potential central bottleneck | Composition is valuable and gateway ownership is clear |
| Federation | Team-owned subgraphs and distributed schema ownership | Cross-subgraph latency, governance, and failure complexity | Multiple teams have real domain ownership and platform support |
| Subscriptions | Near-real-time server-to-client updates | Long-lived connection and delivery operations | Updates are time-sensitive and polling is inadequate |
| Persisted/allow-listed operations | Limits query shapes and supports caching/control | Reduces ad hoc client query flexibility and requires publishing workflow | Public or high-risk APIs need bounded operations |

## 12. Failure modes and mitigations

| Symptom | Likely cause | Mitigation |
|---|---|---|
| High latency on apparently small query | N+1 lookups, serial resolver calls, downstream tail latency | Add batching, bounded parallelism, read models, and resolver-level tracing |
| Database or service overload | Deep/expensive query or unbounded collection selection | Complexity/depth limits, pagination, rate limits, and operation allow-listing |
| Cross-tenant data exposure | Authorization or DataLoader/cache key omits security context | Enforce object-level authorization and tenant-scoped cache keys |
| Partial response surprises clients | Nullability and resolver error propagation not understood | Define error semantics, test failure cases, and document nullable contracts |
| Cache provides stale or wrong data | Cache key/invalidation incomplete or cross-user sharing | Define cache scope, tenant/user keys, TTL/invalidation, and consistency expectations |
| Schema changes break clients | Field removal or incompatible type/semantic change | Compatibility checks, deprecation, usage monitoring, staged removal |
| Gateway outage affects many clients | GraphQL layer is a centralized critical dependency | Scale and isolate it, use SLOs, load tests, graceful degradation, and clear ownership |
| Subscription resource exhaustion | Unbounded connections or slow consumers | Connection quotas, backpressure, bounded buffers, disconnect policy, and monitoring |

## 13. Observability and operations

Monitor:

- Request latency percentiles by operation name or persisted-query identifier
- Error rate and partial-response rate
- Query depth/complexity and response size
- Resolver duration, downstream call count, and fan-out
- DataLoader batch size and cache hit rates
- Per-dependency latency, timeout, and saturation
- Active subscription connections, message lag, and slow-consumer counts
- Deprecated-field usage and schema composition failures

Use traces to connect a GraphQL operation to resolver and downstream work. Redact sensitive variables and avoid unbounded metric labels such as raw query text.

## 14. Staff-level talking points

> I view GraphQL as a client-facing contract and composition layer, not a universal replacement for REST or RPC. I would choose it when multiple clients have materially different data needs and graph-shaped aggregation is valuable.

> The query’s size does not predict its cost. I would budget resolver fan-out, enforce pagination and complexity limits, and measure operation-level latency and downstream work.

> I would keep domain ownership in the underlying services and define explicit schema ownership. If the gateway simply passes through calls, adds latency, and provides no client flexibility or governance benefit, I would avoid adding it.

> I would compare the operational cost—authorization, caching, observability, and query controls—with the client and team benefits before committing to GraphQL.

## 15. Common interview questions

### Is GraphQL faster than REST?

**Tests:** Whether performance is evaluated end-to-end.

**Answer outline:** Not inherently. It can reduce client round trips and unnecessary payload fields, but resolver fan-out, N+1 queries, and gateway overhead can make it slower. Measure user-visible latency and backend work.

### How do you prevent expensive queries?

**Tests:** Operational and abuse awareness.

**Answer outline:** Require pagination, cap depth and complexity, enforce timeouts and rate limits, batch data access, and consider persisted/allow-listed operations depending on client model and risk.

### How do you solve N+1 queries?

**Tests:** Resolver execution understanding.

**Answer outline:** Batch related lookups, use request-scoped loaders, avoid serial resolver calls, and consider a read model for expensive graph composition. Ensure cache isolation and bounded batch sizes.

### How should authorization work?

**Tests:** Security design.

**Answer outline:** Authenticate the request, then authorize access to resources and fields according to domain policy. Do not rely solely on hiding schema fields or checking only the root operation; ensure batched/cached results preserve tenant and user boundaries.

### When would you choose REST instead?

**Tests:** Architectural judgment.

**Answer outline:** Stable resource APIs, predictable representations, HTTP caching, simple CRUD, or cases where flexible query execution and its governance cost provide little value.

### Does GraphQL replace backend services?

**Tests:** Boundary and ownership reasoning.

**Answer outline:** Usually no. It can compose existing services for clients, but business rules and data ownership should remain in the responsible domain services.

## 16. Mistakes to avoid

- Claiming GraphQL is always faster or always reduces backend load.
- Treating one HTTP request as one backend operation.
- Ignoring N+1, fan-out, query depth, collection size, or tail latency.
- Treating GraphQL as a substitute for service boundaries or domain ownership.
- Assuming a typed schema automatically provides authorization or security.
- Claiming GraphQL cannot be cached; instead, explain that caching strategy differs and may require deliberate design.
- Adopting federation before there is a real ownership or scaling need.
- Using subscriptions without connection, backpressure, and delivery policies.
- Ignoring REST, RPC, and events as complementary choices.

## 17. Quick revision

- GraphQL is a typed query language/runtime and client-facing data graph.
- Its main strength is flexible, typed data selection and composition for varied clients.
- It is not inherently faster and does not remove backend service boundaries.
- Resolvers can create N+1 queries and fan-out; batch and measure them.
- Enforce pagination, query-cost limits, timeouts, and authorization.
- REST often fits stable resource APIs and HTTP caching.
- gRPC/RPC often fits explicit internal service calls.
- Events fit asynchronous integration and workflows.
- Use GraphQL when client flexibility/composition benefits justify operational complexity.
- Avoid it when requirements are simple or the team cannot safely bound and operate queries.
