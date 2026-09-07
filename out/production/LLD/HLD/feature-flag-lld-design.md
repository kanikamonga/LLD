# Hierarchical Feature Flag LLD Design

## Scope

The implementation supports channels with explicit boolean flags and ordered
parent channels. A lookup uses the current channel first, then performs
depth-first traversal through parents. The first explicitly defined value wins;
missing flags default to `false`.

Implementation root:

`src\LLD\FeatureFlag`

## Object model

```text
FeatureFlagService
    |
    +--> Channel registry
    +--> Lookup cache
    |
    +--> Channel
             +--> explicit feature map
             +--> ordered parent IDs
```

### `Channel`

Owns one channel's ID, explicit feature values, and ordered parent references.
It does not resolve inheritance; that behavior belongs to the service.

### `FeatureFlagService`

Coordinates channel creation, lookup, updates, deletion, hierarchy validation,
and cache invalidation.

### Exceptions

Dedicated exceptions make invalid operations explicit:

- Duplicate channel
- Missing channel
- Invalid hierarchy
- Deleting a channel with children

## Inheritance semantics

For:

```text
mobile -> [india, global]
india  -> [global]
```

The lookup order is:

```text
mobile, india, global, then the next parent branch
```

An explicit `false` is a valid answer and stops traversal. It must not be
confused with “the flag was not found.” Internally, the resolver therefore uses
`null` to mean missing and `Boolean.FALSE` to mean an explicit false value.

## SOLID principles

### Single Responsibility Principle

- `Channel` stores channel state.
- `FeatureFlagService` manages hierarchy and resolution.
- Exception classes represent distinct failure conditions.
- `FeatureFlagDemo` only demonstrates usage.

### Open/Closed Principle

The channel-resolution policy can later be extracted behind an interface, for
example:

```java
interface FeatureResolver {
    boolean resolve(String channelId, String featureName);
}
```

New resolution policies such as priority-based or environment-based lookup can
then be added without changing channel storage.

### Liskov Substitution Principle

The design currently favors composition and does not introduce inheritance where
there is no meaningful subtype relationship.

### Interface Segregation Principle

The current in-memory solution does not create artificial interfaces. If storage
is externalized, a focused `ChannelRepository` interface can be introduced
instead of exposing unrelated persistence operations.

### Dependency Inversion Principle

The current service is intentionally in-memory. For production, channel storage,
cache, and resolver policy should be injected as interfaces so the service does
not depend on a particular database or cache.

## Design patterns and concepts

### Composite-like hierarchy

Channels form a directed acyclic graph rather than a simple tree because a
channel can have multiple parents. The service traverses this graph recursively.

### Cache-aside/read-through lookup

`getFeature()` first checks the lookup cache. On a miss, it resolves the value
from the hierarchy and stores the result. This improves repeated reads in a
read-heavy workload.

### Encapsulation

The channel's feature map and parent list are exposed as unmodifiable views.
Mutation occurs through controlled service operations.

### Fail-fast validation

Channel creation rejects:

- Duplicate IDs
- Missing parents
- Self-parenting
- Duplicate parents
- Cycles

Deleting a channel with children is rejected rather than silently changing their
behavior.

## Cache consistency

When any feature changes, the service clears the lookup cache. This is simple and
strongly consistent within the JVM:

```text
set feature -> update explicit value -> invalidate cache
```

An update high in the hierarchy can affect every descendant, so stale descendant
entries must not survive.

For larger systems, improve this with:

- A configuration version per channel or hierarchy root
- Cache entries containing the version they were resolved against
- Reverse child indexes for targeted descendant invalidation
- Distributed cache invalidation events
- Versioned snapshots for atomic reads

The trade-off is that targeted invalidation uses more metadata, while global
invalidation is simpler but may reduce cache hit rate after updates.

## Concurrency

Public service operations are synchronized, so a lookup cannot observe a
half-applied channel update or a partially validated hierarchy.

For production:

- Store channels in a transactional database.
- Use immutable configuration snapshots for high read concurrency.
- Publish update events after a committed change.
- Use optimistic version checks to prevent lost updates.
- Ensure every application instance invalidates or refreshes its cache.

## Complexity

Let `V` be the number of reachable channels and `E` the parent links:

- Uncached lookup: `O(V + E)` worst case
- Cached lookup: average `O(1)`
- Create validation: `O(V + E)` worst case
- Feature update: `O(1)` plus cache invalidation
- Delete validation: `O(number of channels + parent links)`

## Testing

The demo covers:

- Explicit current-channel values
- Inherited values
- Multiple parent order
- Missing flag defaulting to false
- Parent updates invalidating cached results
- Safe deletion of a leaf
- Rejection of deleting a channel with children

Additional tests should cover explicit false precedence, cycle rejection,
concurrent reads and updates, deep hierarchies, and large fan-out graphs.
