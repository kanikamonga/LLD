# SOLID Principles — Staff Backend Engineer Interview Notes

## Overview

**SOLID** is a set of five object-oriented design principles that help manage change, dependencies, and substitutability:

- **S**ingle Responsibility Principle
- **O**pen-Closed Principle
- **L**iskov Substitution Principle
- **I**nterface Segregation Principle
- **D**ependency Inversion Principle

They are not rules to apply mechanically. Their purpose is to make likely changes local, contracts dependable, and high-level policy less coupled to implementation detail. Applying them indiscriminately can add unnecessary interfaces, indirection, and complexity.

The examples use Java-like code sketches.

## 1. Single Responsibility Principle (SRP)

### What it stands for

**A module should have one reason to change.**

“Responsibility” means a cohesive area of behavior or a stakeholder-driven reason for change—not necessarily one method or one tiny class.

### Intent and why it matters

Keep behavior that changes for different reasons from becoming entangled in one module. This improves cohesion and makes changes, tests, and ownership easier to reason about.

### When it helps

- A class mixes business rules with persistence, transport, formatting, or notification delivery.
- A change to one concern risks breaking unrelated behavior.
- The class has several unrelated collaborators or is difficult to test without infrastructure.

### Example

Instead of one service that applies pricing, persists orders, and sends email:

```java
class OrderService {
    Money calculateTotal(Order order) { /* pricing rules */ }
    void save(Order order) { /* database code */ }
    void sendConfirmation(Order order) { /* email code */ }
}
```

Separate the responsibilities:

```java
class PricingService {
    Money calculateTotal(Order order) { /* pricing rules */ }
}

interface OrderRepository {
    void save(Order order);
}

interface OrderNotifier {
    void sendConfirmation(Order order);
}

class PlaceOrder {
    private final PricingService pricing;
    private final OrderRepository orders;
    private final OrderNotifier notifier;

    PlaceOrder(PricingService pricing, OrderRepository orders,
               OrderNotifier notifier) {
        this.pricing = pricing;
        this.orders = orders;
        this.notifier = notifier;
    }

    void execute(Order order) {
        order.setTotal(pricing.calculateTotal(order));
        orders.save(order);
        notifier.sendConfirmation(order);
    }
}
```

The application flow still coordinates the use case, while pricing, storage, and notification policies can change independently.

### Common pitfall

Do not split every method into its own class. SRP is about cohesive reasons to change; excessive fragmentation increases navigation and coordination costs.

## 2. Open-Closed Principle (OCP)

### What it stands for

**Software entities should be open for extension but closed for modification.**

Existing code should not need repeated edits every time a new supported behavior is added, when that behavior can be represented through a stable abstraction.

### Intent and why it matters

Reduce regression risk by letting new variants be added as new implementations rather than repeatedly changing a central conditional. OCP is most useful where variation is expected and bounded by a meaningful contract.

### When it helps

- New payment methods, delivery strategies, export formats, or policy variants are likely.
- A growing `if`/`switch` statement centralizes behavior that should vary independently.
- Different implementations can share a stable contract.

### Example

An expanding conditional ties the checkout flow to every payment method:

```java
Money feeFor(PaymentMethod method, Money amount) {
    if (method == CARD) return amount.multiply(0.03);
    if (method == BANK_TRANSFER) return Money.ZERO;
    throw new IllegalArgumentException("Unsupported method");
}
```

Represent the variable behavior through a strategy:

```java
interface PaymentFeePolicy {
    boolean supports(PaymentMethod method);
    Money feeFor(Money amount);
}

class CardFeePolicy implements PaymentFeePolicy {
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }

    public Money feeFor(Money amount) {
        return amount.multiply(0.03);
    }
}

class NoFeePolicy implements PaymentFeePolicy {
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.BANK_TRANSFER;
    }

    public Money feeFor(Money amount) {
        return Money.ZERO;
    }
}
```

The checkout workflow can use a policy selected by configuration or a registry. Adding a new method then adds an implementation and registration rather than changing the fee algorithm's central conditional.

### Common pitfall

OCP does not mean “never modify existing code.” Requirements can change, and abstractions have a cost. Introduce extension points where variation is probable and valuable—not speculatively for every possible future feature.

## 3. Liskov Substitution Principle (LSP)

### What it stands for

**Objects of a subtype should be usable wherever objects of the base type are expected without breaking correctness.**

### Intent and why it matters

Inheritance and interface implementation create behavioral contracts, not only matching method signatures. A subtype must preserve the expectations callers rely on.

In contract terms, a subtype should not:

- Strengthen preconditions (require more from callers).
- Weaken postconditions (promise less than the base contract).
- Break invariants or expected side effects.

### When it helps

- Modeling subtypes behind a common interface or base class.
- Designing plugin systems or interchangeable implementations.
- Reviewing whether inheritance represents a genuine “is-a” behavioral relationship.

### Example

Suppose the contract says a writable document store accepts writes:

```java
interface DocumentStore {
    Document load(DocumentId id);
    void save(Document document);
}
```

A read-only implementation that throws from `save` is not a valid substitute:

```java
class ReadOnlyDocumentStore implements DocumentStore {
    public Document load(DocumentId id) { /* ... */ }

    public void save(Document document) {
        throw new UnsupportedOperationException();
    }
}
```

Callers written against `DocumentStore` reasonably expect `save` to work. Split the contracts:

```java
interface DocumentReader {
    Document load(DocumentId id);
}

interface DocumentWriter {
    void save(Document document);
}

class ReadOnlyDocumentStore implements DocumentReader {
    public Document load(DocumentId id) { /* ... */ }
}
```

Now each implementation only claims behaviors it can honor.

### Common pitfall

A subtype is not valid merely because it compiles or shares the same method signatures. Check observable behavior, error conditions, invariants, and side effects. If subclasses need to disable or reinterpret base behavior, composition or a narrower interface may be more accurate.

## 4. Interface Segregation Principle (ISP)

### What it stands for

**Clients should not be forced to depend on methods they do not use.**

### Intent and why it matters

Keep interfaces focused on client needs so implementations do not carry irrelevant methods, dummy implementations, or unsupported-operation exceptions. Smaller contracts reduce coupling and make behavior clearer.

### When it helps

- An interface has unrelated groups of methods used by different clients.
- Implementations leave methods empty or throw “not supported.”
- A change to one capability forces unrelated clients to recompile or change.

### Example

One large device interface burdens simple devices with capabilities they do not have:

```java
interface OfficeDevice {
    void print(Document document);
    void scan(Document document);
    void fax(Document document);
}
```

Separate capabilities by client need:

```java
interface Printer {
    void print(Document document);
}

interface Scanner {
    ScannedDocument scan(Document document);
}

interface Fax {
    void sendFax(FaxNumber recipient, Document document);
}
```

A basic printer implements only `Printer`; a multifunction device can implement all three. Clients depend only on the capabilities they use.

### Common pitfall

Do not create a large number of tiny interfaces without meaningful client boundaries. Segregate by cohesive capability and actual usage, not simply to minimize method count.

## 5. Dependency Inversion Principle (DIP)

### What it stands for

1. **High-level modules should not depend on low-level modules. Both should depend on abstractions.**
2. **Abstractions should not depend on details. Details should depend on abstractions.**

### Intent and why it matters

Keep business policy independent of infrastructure details such as databases, email providers, or external APIs. This makes policy easier to test and allows implementation details to change without rewriting core workflows.

The direction of source-code dependency should point toward the policy abstraction, even if runtime control flows outward to a database or remote service.

### When it helps

- Core business workflows directly construct infrastructure clients.
- Unit tests need a real database or remote service just to exercise policy.
- Infrastructure choices may change or require alternative implementations.

### Example

The high-level use case should depend on a repository abstraction, not a concrete database adapter:

```java
interface OrderRepository {
    void save(Order order);
}

class PlaceOrder {
    private final OrderRepository orders;

    PlaceOrder(OrderRepository orders) {
        this.orders = orders;
    }

    void execute(Order order) {
        // Apply business rules.
        orders.save(order);
    }
}

class PostgresOrderRepository implements OrderRepository {
    public void save(Order order) {
        // SQL and database-specific implementation.
    }
}
```

Dependency injection wires `PostgresOrderRepository` into `PlaceOrder` at the application boundary. Tests can supply an in-memory or fake implementation.

### Common pitfall

DIP is not “add an interface for every class.” An abstraction is useful when it protects a policy boundary, supports meaningful substitution, or makes a dependency direction intentional. Interfaces that merely mirror one implementation can add ceremony without reducing coupling.

## 6. How the five principles differ and work together

| Principle | Main question | Primary concern |
|---|---|---|
| **SRP** | Does this module change for unrelated reasons? | Cohesion and change boundaries |
| **OCP** | Can likely new variants be added without editing stable policy repeatedly? | Extension and change risk |
| **LSP** | Can this implementation honor the contract callers expect? | Behavioral substitutability |
| **ISP** | Does each client depend only on capabilities it uses? | Client coupling and contract size |
| **DIP** | Does core policy depend on infrastructure details? | Dependency direction and policy isolation |

Together, they support maintainable software:

1. **SRP** identifies cohesive responsibilities.
2. **ISP** defines focused contracts for those responsibilities.
3. **DIP** lets high-level policy depend on those contracts rather than infrastructure.
4. **LSP** ensures implementations can safely fulfill the contracts.
5. **OCP** makes it practical to add anticipated variants behind stable contracts.

Example: an order-placement workflow (SRP) depends on a focused `PaymentProcessor` abstraction (ISP, DIP). Card and bank-transfer processors honor the same behavioral contract (LSP). A new processor can be added without rewriting the workflow (OCP).

## 7. Staff-level application and trade-offs

- Start from **change drivers, business invariants, and client needs**, not from a desire to apply patterns.
- Use interfaces at real boundaries: external systems, likely variants, ownership boundaries, or policy-versus-detail seams.
- Prefer composition for behavior combinations; inheritance is appropriate only when the subtype preserves the base contract.
- Do not optimize for hypothetical future requirements. Every abstraction has design, testing, and cognitive cost.
- In a distributed system, these principles apply to service contracts too: focused APIs (ISP), compatible behavior (LSP-like substitutability), stable extension (OCP), clear ownership (SRP), and policy isolated from vendor details (DIP).
- Test observable contracts and invariants, not only method calls or implementation structure.

## 8. Common interview questions

### Is SRP the “one method per class” principle?

No. It is about one cohesive responsibility or reason to change, not a prescribed class size or method count.

### Does OCP mean existing code must never change?

No. It means likely variation can be added through suitable extension points instead of repeatedly modifying stable policy. Abstractions are justified only when their benefits exceed their cost.

### How are ISP and SRP different?

SRP is about a module's reasons to change. ISP is about keeping an interface focused on the needs of its clients. They often reinforce one another but address different coupling problems.

### How are DIP and dependency injection different?

DIP is a design principle about dependency direction. Dependency injection is a technique for supplying dependencies. A system can use dependency injection while still depending on poorly chosen abstractions.

### How do you recognize an LSP violation?

Look for a subtype that rejects valid base operations, strengthens preconditions, weakens guarantees, changes expected invariants, or surprises callers with incompatible side effects.

## 9. Mistakes to avoid

- Treating SOLID as five mandatory patterns or rules for every class.
- Confusing SRP with one method per class.
- Treating OCP as a ban on modifying existing code.
- Assuming matching method signatures guarantee LSP.
- Making interfaces tiny without client or capability cohesion.
- Adding interfaces everywhere and calling it DIP.
- Using inheritance where composition or a narrower contract is more honest.
- Ignoring the maintenance cost of speculative abstractions.

## 10. Quick revision

- **S — Single Responsibility:** one cohesive reason to change.
- **O — Open-Closed:** extend likely variation without repeatedly modifying stable policy.
- **L — Liskov Substitution:** implementations honor the behavioral contract callers expect.
- **I — Interface Segregation:** clients depend only on capabilities they use.
- **D — Dependency Inversion:** high-level policy and low-level details depend on abstractions.
- SOLID is a tool for managing change and coupling, not an end in itself.
- Apply principles where they protect real boundaries and invariants; avoid speculative abstraction.
