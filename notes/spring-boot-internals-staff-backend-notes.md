# Spring Boot Internals — Staff Backend Engineer Interview Notes

## 1. Staff-level summary

Spring Boot builds on the Spring Framework to create and configure an application context with sensible defaults. Its core startup work is environment preparation, auto-configuration, bean registration and dependency injection, followed by starting the selected application runtime, such as an embedded web server.

## 2. Mental model

Think of Spring Boot as an **application bootstrapper and conditional configuration system** around Spring's IoC container. It does not replace Spring: it assembles a context from application code, libraries, configuration properties, and conditional auto-configuration.

Boot helps with:

- Convention-based configuration
- Dependency and version alignment through starters and the Boot dependency-management model
- Embedded servers and executable packaging
- Externalized configuration
- Production integration through Actuator and related libraries

Boot does not remove the need to understand bean lifecycle, dependency graphs, configuration precedence, or runtime behavior.

## 3. Startup lifecycle

A simplified startup sequence:

```text
main()
  → SpringApplication.run(...)
  → prepare Environment and load configuration
  → create ApplicationContext
  → register sources and process configuration classes
  → evaluate auto-configuration and component scanning
  → create and initialize singleton beans
  → refresh context
  → start embedded web server (web application)
  → publish application-ready events
```

The exact ordering has extension points and can vary by application type and Boot version.

### `SpringApplication`

`SpringApplication.run` orchestrates startup. It determines the application type, prepares the environment, creates the appropriate context, loads sources, refreshes the context, and invokes lifecycle callbacks and listeners.

### Environment and configuration

The `Environment` exposes property sources and profiles used during bean creation and configuration binding. Values can come from configuration files, environment variables, command-line arguments, system properties, and other configured sources. Precedence matters: the winning value depends on the property-source ordering and Boot version.

Use `@ConfigurationProperties` for grouped, typed configuration. Validate important configuration at startup so invalid deployments fail early.

### Application context and bean factory

The `ApplicationContext` is Spring's container and higher-level application registry. Its underlying bean factory manages bean definitions, dependency resolution, scopes, lifecycle callbacks, and creation.

Most application beans are singletons by default. This means one bean instance per context—not one instance per request—and mutable singleton state must be made thread-safe or avoided.

### Configuration processing and component scanning

`@SpringBootApplication` combines commonly used configuration behavior, including configuration-class processing, auto-configuration enablement, and component scanning. Component scanning finds eligible annotated classes under its configured package boundaries. Keep the main application class in a package that naturally covers the application components, or explicitly configure scanning.

### Auto-configuration

Auto-configuration contributes bean definitions based on the classpath, existing beans, properties, and application type. It is conditional, not unconditional magic.

For example, a web-related auto-configuration may activate when the relevant classes are present and back off when the application supplies a bean that should override a default. Exact conditions and bean names are implementation- and version-dependent.

When debugging unexpected configuration, inspect the condition evaluation report, startup logs, and the relevant auto-configuration class rather than guessing.

### Starters

Starters are dependency descriptors that bring together commonly used libraries. They do not themselves implement runtime behavior; the libraries they include make relevant auto-configuration possible.

## 4. Why interviewers care

Understanding internals helps explain:

- Why a bean exists, is missing, or has the wrong implementation
- How configuration changes the runtime without code changes
- Startup-time failures and initialization order
- Why a service behaves differently across environments
- How to keep auto-configuration convenient without losing control
- How bean scope and proxies affect request concurrency and transactions

## 5. Core concepts

| Concept | What it means | Why it matters |
|---|---|---|
| IoC / dependency injection | Container creates objects and supplies dependencies | Reduces construction coupling; makes wiring and lifecycle explicit |
| Bean definition | Metadata describing how a bean is created | Configuration processing contributes and modifies these definitions |
| Profiles | Environment-specific configuration and bean conditions | Useful for deployment variants, but avoid treating profiles as a substitute for sound configuration |
| `@ConfigurationProperties` | Binds a property group to a typed object | Safer and more maintainable than scattered string lookups |
| Bean post-processors | Hooks around bean creation and initialization | Used by framework features such as proxies and annotations |
| Proxies | Wrappers that intercept method calls | Enable transactions, security, caching, and AOP; self-invocation and proxy boundaries can surprise |
| Embedded server | Web runtime started within the application process | Simplifies packaging, but server and application lifecycle are coupled |
| Actuator | Operational endpoints and integrations | Supports health, metrics, and diagnostics; endpoints need deliberate exposure and access control |

## 6. Production design considerations

- Keep configuration externalized, typed, validated, and documented.
- Avoid broad component scans that accidentally register unrelated classes.
- Prefer explicit application-owned beans when defaults are insufficient; understand the conditional backoff behavior.
- Keep singleton services stateless where possible. Request-specific mutable state belongs in method-local variables or a suitable scoped component.
- Know proxy constraints: calls through the proxy are intercepted; direct self-invocation commonly bypasses proxy advice.
- Avoid expensive or failure-prone network calls during bean initialization unless startup must depend on them. Otherwise, use explicit readiness and lifecycle design.
- Treat Actuator endpoints as operational APIs: expose only what is required and secure sensitive details.
- For startup and shutdown, account for migrations, readiness, graceful termination, and orchestrator behavior.
- Use Boot's dependency management deliberately; inspect resolved dependencies when diagnosing classpath conflicts.

## 7. Trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| Rely on auto-configuration | Fast setup and conventional defaults | Hidden conditions can obscure behavior | Standard integrations with well-understood defaults |
| Override with explicit beans | Clear application control | More configuration to maintain | Defaults do not meet requirements or need customization |
| Use profiles | Convenient environment-specific activation | Profile combinations can become hard to reason about | Small, clear deployment variants |
| Bind with `@ConfigurationProperties` | Typed, validated configuration | Requires a defined configuration model | Related operational settings or public application configuration |
| Eager initialization | Fail early and simplify first-use behavior | Slower startup and startup dependency failures | Required local initialization with bounded cost |
| Lazy initialization | Faster startup in some applications | Failures move to first use; resource spikes possible | Optional components or controlled startup constraints |

## 8. Failure modes and mitigations

| Symptom | Likely cause | Mitigation |
|---|---|---|
| Missing bean | Component outside scan boundary, condition did not match, profile or configuration not active | Inspect startup condition report and bean definitions; fix package boundaries or explicit configuration |
| Unexpected bean implementation | Auto-configuration condition or override behavior differs from expectation | Inspect condition report and bean names; define an intentional replacement and test context wiring |
| Property has unexpected value | Different property source wins, profile mismatch, or binding name/type issue | Inspect effective environment and binding; validate required values |
| Transaction or cache advice not applied | Invocation bypasses proxy, method visibility/proxy constraints, or bean is not proxied | Call through the managed bean boundary; verify proxy and transaction configuration |
| Startup timeout | Slow bean initialization, external dependency wait, migration, or classpath/config problem | Measure startup phases; bound external calls; distinguish liveness from readiness |
| Unexpected shared-state race | Mutable singleton used concurrently | Remove shared mutable state or use correct synchronization/concurrent primitives |

## 9. Observability and operations

- Track startup duration, failure phase, and readiness transitions.
- Use structured logs with application and trace context; avoid logging secrets or full sensitive configuration.
- Monitor request latency, error rate, saturation, JVM memory, garbage collection, thread pools, and database pools.
- Use Actuator health and metrics endpoints selectively; secure management endpoints and avoid leaking environment details.
- Expose readiness only when the service can handle traffic; liveness should indicate whether restarting is appropriate, not whether every dependency is healthy.
- Include configuration validation and dependency/version checks in deployment and CI workflows.

## 10. Staff-level talking points

> I treat Spring Boot's defaults as conditional configuration, not magic. When behavior is unexpected, I trace the environment, configuration properties, bean definitions, and auto-configuration conditions before adding overrides.

> I pay particular attention to bean scope and proxy boundaries. A singleton service is shared across concurrent requests, and annotations such as transaction management work through interception boundaries that can be bypassed by direct self-invocation.

> I also separate process health from traffic readiness. A service can be alive while not ready to serve, and a transient downstream outage should not automatically cause every instance to restart.

## 11. Common interview questions

### How does Spring Boot auto-configuration work?

**Tests:** Understanding of conditions and context construction.

**Answer outline:** Boot imports auto-configuration candidates; conditions evaluate classpath, beans, properties, and application type; matching configurations contribute bean definitions; many defaults back off when an application-defined bean exists. The condition report helps diagnose decisions.

### What happens when `SpringApplication.run` is called?

**Tests:** Startup lifecycle understanding.

**Answer outline:** Boot prepares the environment, creates the application context, loads configuration and bean definitions, refreshes the context (creating non-lazy singletons), starts the embedded server for web applications, then publishes lifecycle events.

### Why might `@Transactional` not take effect?

**Tests:** Proxy and AOP understanding.

**Answer outline:** Transaction advice is typically applied through a proxy. Calls that bypass the proxy—commonly self-invocation—or unsupported method/proxy arrangements may not be intercepted. Verify the actual bean proxy and transaction manager.

### What is the difference between a starter and auto-configuration?

**Tests:** Dependency versus runtime configuration.

**Answer outline:** A starter brings in a curated dependency set. Auto-configuration conditionally registers beans based on the classpath and application configuration.

## 12. Mistakes to avoid

- Calling Boot auto-configuration unconditional magic.
- Treating starters as the components that perform the runtime work.
- Assuming a singleton bean is created once per request.
- Assuming annotations always apply regardless of proxy boundaries.
- Adding configuration overrides before checking why the default condition matched.
- Exposing all management endpoints without considering access and information disclosure.

## 13. Quick revision

- Boot orchestrates startup; Spring's context manages beans and dependency injection.
- `@SpringBootApplication` combines common configuration, scanning, and auto-configuration behavior.
- Auto-configuration is conditional on classpath, beans, properties, and application type.
- Starters primarily provide curated dependencies.
- Configuration precedence and profiles affect effective values.
- Singleton means one instance per context; concurrent requests share it.
- Proxy-based features require calls to cross the proxy boundary.
- Diagnose with condition reports, startup logs, and effective configuration.
- Design health, readiness, graceful shutdown, and management endpoint security intentionally.
