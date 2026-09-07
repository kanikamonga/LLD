# Food Ordering System - Design & Implementation

Location: `src/LLD/foodordering`

## Requirements recap
- Restaurants onboard with a menu (item -> price), a rating (1-5), and a max
  concurrent-order capacity.
- Orders auto-assign to a single restaurant that can fulfill ALL requested
  items, chosen by a pluggable selection criteria (lowest cost / highest
  rating).
- A restaurant can only hold as many ACCEPTED orders as its capacity allows;
  once COMPLETED, that slot frees up.
- Restaurants can mark ACCEPTED -> COMPLETED, but can never cancel an
  ACCEPTED order.
- Menus can be added to / price-updated, but items are never deleted.
- No external DB - everything in-memory.

## Class model

```text
RestaurantService                 OrderService
   owns Map<name, Restaurant>         owns Map<id, Order>
   - onboardRestaurant()               - placeOrder(customer, items, strategy)
   - addMenuItem()                     - completeOrder(orderId)
   - updateMenuItemPrice()             - getOrder(orderId)
        |                                    |
        v                                    v
   Restaurant  <---------------------- RestaurantSelectionStrategy (interface)
   - menu: Map<String, MenuItem>            ^        ^
   - rating, maxConcurrentOrders            |        |
   - currentActiveOrders          LowestCostStrategy  HighestRatingStrategy
   - canFulfill(items)
   - quoteCost(items)
   - tryReserveCapacity() / releaseCapacity()   (atomic, lock-guarded)

   Order
   - id, customerName, items (immutable), selectionStrategy
   - status: PENDING -> ACCEPTED -> COMPLETED
                       -> REJECTED
   - assignedRestaurant
```

**Relationships**
- `RestaurantService` *has-a* collection of `Restaurant` (composition: restaurants only exist through this service).
- `Restaurant` *has-a* `Map<String, MenuItem>` (composition - items belong to exactly one restaurant).
- `OrderService` *depends-on* `RestaurantService` (reads the restaurant registry to find eligible candidates) and *has-a* collection of `Order`.
- `Order` *has-a* reference to the `RestaurantSelectionStrategy` used at placement time (each order can pick its own criteria) and, once accepted, a reference to its `Restaurant`.
- `Restaurant` and `Order` do **not** know about each other's services - `OrderService` mediates all interaction, keeping restaurant/menu management and order assignment as independent concerns.

## Why these responsibilities live where they do
- **Restaurant** owns menu data and capacity accounting because it is the only entity that can answer "can I serve this?" / "what would it cost?" / "do I have room right now?" without external coordination. Encapsulating `tryReserveCapacity()` inside `Restaurant` (rather than `OrderService` doing `if (count < max) count++`) means the check-and-increment is atomic under the restaurant's own lock - no caller can forget to synchronize it.
- **OrderService** owns the *assignment algorithm* (filter eligible -> rank via strategy -> reserve capacity in ranked order, falling back on failure) because that logic spans multiple restaurants and doesn't belong to any single one.
- **RestaurantSelectionStrategy** is extracted as its own interface (Strategy pattern) because "how we pick among eligible restaurants" is exactly the part of the system explicitly required to vary (lowest cost today, highest rating today, maybe "fastest ETA" tomorrow) while the surrounding assignment/capacity logic stays fixed. This is the textbook motivating case for Strategy: *object creation isn't varying, a behavior (comparison/ranking) is, and the caller (OrderService) should depend on the abstraction, not on `if (criteria.equals("lowest cost"))`.*

## Design patterns used (and why, not just "which")
- **Strategy** (`RestaurantSelectionStrategy` + `LowestCostStrategy` / `HighestRatingStrategy`): isolates the varying "ranking" behavior from the stable "assign order" workflow. New criteria (e.g. fastest-prep-time) are added by implementing the interface - zero changes to `OrderService` (Open/Closed Principle).
- No Factory/Builder was introduced for `Restaurant`/`Order` construction because there is only one concrete way to build each and no varying creation logic - adding one would be unjustified over-engineering for this problem size.

## SOLID
- **S**: `Restaurant` = menu/capacity; `OrderService` = assignment/lifecycle; `RestaurantService` = restaurant registry/menu edits; each strategy = one ranking rule.
- **O**: new selection criteria or new order-rejection reasons plug in without modifying existing classes.
- **L**: any `RestaurantSelectionStrategy` implementation is fully substitutable by `OrderService` - it only calls `rank()`.
- **I**: `RestaurantSelectionStrategy` has exactly one method - no fat interface forcing unused methods on implementers.
- **D**: `OrderService` depends on the `RestaurantSelectionStrategy` abstraction, not concrete strategy classes; the concrete strategy is supplied by the caller per-order (dependency injection at the call site).

## Concurrency handling
- `Restaurant.tryReserveCapacity()` performs the capacity check-and-increment under a single `ReentrantLock`, so two threads racing to book the last slot cannot both succeed (verified by `concurrentOrdersRespectSingleSlotCapacity` test: 50 concurrent placements against a capacity-1 restaurant yield exactly 1 ACCEPTED).
- Menu reads/writes (`addMenuItem`, `updateItemPrice`, `canFulfill`, `quoteCost`) are guarded by the same per-restaurant lock, so a menu update racing with an order-cost calculation can't see a half-updated item.
- `RestaurantService`/`OrderService` use `ConcurrentHashMap` for their registries (restaurant-by-name, order-by-id) so registry lookups/inserts from multiple threads are safe without a global lock.
- Locking is **per-restaurant**, not global - orders for different restaurants never contend with each other, only orders competing for the *same* restaurant's capacity do.

## Error handling
Custom unchecked exceptions communicate specific failure modes instead of generic `RuntimeException`/return codes:
- `DuplicateRestaurantException` - onboarding a name that's already taken.
- `RestaurantNotFoundException` / `OrderNotFoundException` - unknown id/name lookups.
- `MenuItemNotFoundException` - updating a price for an item that was never added.
- `NoRestaurantAvailableException` - no restaurant's menu covers all items, or all eligible restaurants are at capacity (order is still recorded as `REJECTED` for audit).
- `InvalidOrderStateException` - e.g. completing an order that isn't `ACCEPTED` (also the natural guard preventing "cancel an ACCEPTED order").

## Extensibility (what changes if requirements evolve)
- **New selection criteria** (e.g. fastest prep time, nearest distance): add a class implementing `RestaurantSelectionStrategy`; nothing else changes.
- **Partial fulfillment across restaurants**: would require a new `OrderFulfillmentStrategy` concept at the `OrderService` level (today's `assignToRestaurant` assumes single-restaurant fulfillment by design, per requirements) - isolated to that one method.
- **Persistent storage**: swap the `ConcurrentHashMap`-backed registries in `RestaurantService`/`OrderService` for repository interfaces backed by a DB - the domain classes (`Restaurant`, `Order`, `MenuItem`) need no changes.
- **Order cancellation before acceptance**: since assignment currently happens synchronously inside `placeOrder`, there's no window where an order is `PENDING` and cancellable; introducing an async/queued assignment flow would reopen that window and need a `cancelOrder()` guarded by `InvalidOrderStateException` when status is already `ACCEPTED`.

## Testing
`FoodOrderingSystemTest` (plain-`main` harness, consistent with this repo's existing `SingletonTest` convention - no build tool/JUnit wired up) covers:
- Lowest-cost and highest-rating strategy selection.
- Rejection when no restaurant's menu covers all items.
- Capacity enforcement + release-on-completion + re-availability afterwards.
- Rejecting a double-complete (`InvalidOrderStateException`).
- Menu add/update behavior and rejecting updates to nonexistent items.
- **Concurrency**: 50 threads racing to book a capacity-1 restaurant -> exactly 1 accepted, 49 rejected, verified via `CountDownLatch`-synchronized start.

`FoodOrderingDemo` (plain `main`) reproduces the exact sample scenario from the problem statement end-to-end and prints matching output for all 5 sample orders.

## How to run
```bash
cd src/LLD/foodordering
javac -d /tmp/out *.java exceptions/*.java
java -cp /tmp/out LLD.foodordering.FoodOrderingDemo
java -cp /tmp/out LLD.foodordering.FoodOrderingSystemTest
```
