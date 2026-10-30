# Concurrent coffee machine

Dependency-free Java (JDK 8+) implementation; no HTTP API or build-system changes.

## Run from the repository root

Use a new, named build directory (the `mkdir` intentionally fails if it already exists):

```sh
mkdir .coffee-machine-build &&
javac -Xlint:all -d .coffee-machine-build src/LLD/coffeemachine/*.java &&
java -cp .coffee-machine-build LLD.coffeemachine.CoffeeMachineTest &&
java -cp .coffee-machine-build LLD.coffeemachine.CoffeeMachineDemo src/LLD/coffeemachine/sample.json
```

Order specific beverages, including repeats:

```sh
java -cp .coffee-machine-build LLD.coffeemachine.CoffeeMachineDemo \
  src/LLD/coffeemachine/sample.json hot_tea hot_tea green_tea
```

Afterward remove only that generated directory: `rm -r .coffee-machine-build`.
Invalid configuration, IO errors, or unexpected preparation failures terminate the CLI
with an exception/nonzero exit; ordinary order rejection is printed as an order result.

## Model and concurrency

* `MachineConfig` deep-copies recipes and initial inventory into immutable maps.
  Quantities are `long`; use a consistent unit per ingredient. Recipe amounts must
  be positive. Initial stock may be zero or empty.
* `CoffeeMachine` has a fixed pool of **N** workers/outlets and an unbounded waiting
  queue. Every beverage has the same configurable preparation duration in milliseconds
  (demo: 100 ms). Zero duration is supported. A blocking injectable preparation hook
  enables deterministic tests and real equipment integration; it must return only
  when preparation finishes and cooperate with interruption.
* `order(name)` returns `Future<Result>`. Result statuses distinguish prepared,
  unavailable ingredient (no inventory entry), insufficient quantity (including a
  known ingredient at zero), and unknown beverage. All missing ingredients are checked
  before any insufficiency, independent of recipe iteration order.
* Inventory reservation is all-or-nothing under one short lock. Preparation runs
  **outside** the lock. Stock snapshots show available, unreserved quantities.
  Successful preparation consumes the reservation.
* **Interruption/failure policy:** a hook exception/error or an observed interrupt
  before completion rolls back the complete reservation. Exceptions propagate through
  `Future.get()` as `ExecutionException`. `cancel(true)` requests interruption;
  its future is cancelled immediately, but inventory rollback completes only when the
  worker exits preparation. A hook ignoring interrupts can delay this indefinitely.
  Cancellation racing with completion may be too late to prevent consumption.
  `cancel(false)` does not interrupt an already running preparation, which may finish
  and consume ingredients despite the cancelled future. Cancelling queued work consumes
  nothing. Rollback models a recoverable preparation, not physical liquid already poured.
* `refill(ingredient, positiveAmount)` can introduce a missing ingredient. Overflow
  throws `ArithmeticException` without mutation. Stock **plus all outstanding
  reservations** must fit in `long`, preserving rollback capacity during refills.
* Low indicators use a common configurable **inclusive** threshold: available stock
  `<= threshold` is low (demo: 50). Missing recipe ingredients are indicated at zero.
  Snapshots are immutable copies, not live views.
* `close()` is idempotent and nonblocking: new orders/refills throw
  `IllegalStateException`, accepted queued work drains normally. Inspecting stock
  remains allowed. Call `awaitTermination(timeout, unit)` to wait for completion.
  The caller must close the machine; executor threads are not daemon threads.

No scheduling/fairness promise is made for concurrent reservations. With the provided
sample, exactly two of hot tea, hot coffee and black tea can succeed; the third lacks
stock. All three are individually possible successes, but they cannot all succeed
together. Green tea always fails for unavailable `green_mixture` until it is refilled.
The CLI prints in request order, not completion order.

## Exact JSON schema

`sample.json` is the assignment's sample with its invalid trailing comma corrected.

```json
{
  "machine": {
    "outlets": { "count_n": 4 },
    "total_items_quantity": { "ingredient_name": 500 },
    "beverages": {
      "beverage_name": { "ingredient_name": 100 }
    }
  }
}
```

Only the shown structural fields are accepted, all required; unknown structural
fields are rejected. Inventory/recipe ingredient keys and beverage keys are arbitrary
nonblank, case-sensitive names. `count_n` is an integer in `[1, 2147483647]`.
Inventory quantities are integers in `[0, Long.MAX_VALUE]`; recipe quantities
are in `[1, Long.MAX_VALUE]`. `beverages` and each recipe must be nonempty.
Recipes may reference ingredients absent from initial stock.

`ConfigJson` is deliberately isolated and schema-specific. It recognizes objects,
quoted strings (standard JSON escapes including `\uXXXX`), and nonnegative decimal
integers, with standard JSON whitespace and at most eight object nesting levels.
Strings are permitted only as keys by the schema. Arrays, booleans, null, negative
numbers, fractions, exponents, leading zeroes, integer overflow, comments, duplicate
keys (including escaped equivalents), trailing commas, malformed escapes/control
characters, missing fields, and trailing content are explicitly rejected. CLI files
are read as UTF-8. Duration and threshold are constructor options, not JSON fields.

## Verification

`CoffeeMachineTest` is a plain-main integration runner with bounded waits and no
external dependencies. It covers repeated and variable recipes, missing-first errors,
atomic rejection, simultaneous outlet utilization/queue limits, concurrent inventory
contention, refill during preparation, low indicators, adding absent ingredients,
immutable configuration/snapshots, unknown beverages, validation, JSON boundary/error
cases, cancellation before/during execution, interruption rollback, hook failures,
overflow-safe rollback, graceful lifecycle, and 50 sample runs with inventory
conservation checks. Latches establish actual concurrency without sleep-based guesses.
