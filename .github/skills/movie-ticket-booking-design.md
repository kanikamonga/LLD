# Movie Ticket Booking System

## Scope

This is a complete in-memory LLD implementation for a theatre movie booking flow.
It supports:

- Movies, screens, seats, shows, and registered users
- Temporary seat holds during a booking session
- Successful and failed payments
- Configurable maximum payment retries
- Explicit session closure
- Configurable booking-session timeout
- Concurrent seat selection without double allocation
- Booking confirmation after successful payment
- Demonstrations for all requested cases

The implementation is in:

`src\LLD\MovieTicketBooking`

Run `LLD.MovieTicketBooking.demo.MovieTicketBookingDemo` to execute the scenarios.

## Class responsibilities

### `Movie`

Stores immutable movie metadata: ID, title, and duration.

### `User`

Stores authenticated user identity. Authentication itself is explicitly outside this
exercise's scope.

### `Seat`

Represents a physical seat on a screen. Its identity is immutable.

### `Screen`

Owns the fixed seat arrangement for a theatre screen.

### `Show`

Associates a movie with a screen and start time. It owns the per-show seat
inventory because the same physical seat can be available for one show and booked
for another.

`Show` also owns the atomic seat transitions:

```text
AVAILABLE -> HELD -> BOOKED
HELD      -> AVAILABLE
```

The seat inventory is protected by a private lock. Validation and mutation happen
inside the same critical section, so two users cannot hold the same seat.

### `BookingSession`

Tracks one user's temporary workflow:

- User
- Show
- Selected seats
- Expiry time
- Payment attempts
- Closed state

It is not persisted in this implementation; the service owns the active sessions.

### `Booking`

Immutable confirmation generated only after payment succeeds and seats transition
to `BOOKED`.

### `PaymentGateway`

An interface that isolates payment processing from booking orchestration. A real
implementation can call an external provider. `ScriptedPaymentGateway` is supplied
for deterministic demonstrations and tests.

### `MovieBookingService`

Application service responsible for:

- Registering and listing shows
- Starting sessions
- Selecting seats
- Calling the payment gateway
- Retrying failed payments
- Closing sessions
- Expiring timed-out sessions
- Creating booking confirmations

It synchronizes session-level operations and delegates seat-level atomicity to
`Show`.

### `MutableClock`

Test clock used to demonstrate timeout behavior without waiting in real time.

## Main flow

### Start session

1. Validate the show.
2. Create a session with `now + sessionTimeout`.
3. Store it as active.

### Select seats

1. Expire old sessions.
2. Validate the session.
3. Ask `Show` to atomically verify all requested seats are available.
4. Mark every requested seat as `HELD`.
5. Add the seats to the session.

The entire group is validated before any seat is held. Therefore, a partial hold
cannot occur if one requested seat is unavailable.

### Payment success

1. Validate the active session and selected seats.
2. Invoke `PaymentGateway`.
3. Ask `Show` to atomically transition the held seats to `BOOKED`.
4. Create and store a `Booking`.
5. Close and remove the session.

### Payment failure

The payment attempt count is incremented. A failure is retryable while the number
of failed attempts has not exceeded `maxPaymentRetries`. Once the retry limit is
exceeded, all held seats are released and the session is closed.

With `maxPaymentRetries = 0`, the first failed payment releases the seats, as
required by the demo.

### Explicit close

The session releases only the seats held by that session and then closes.

### Timeout

`expireSessions()` scans active sessions and releases the seats of expired
sessions. Public operations also call it first, so stale holds are cleaned up
before availability is returned.

For a production system, a scheduled expiry worker would call this operation.

## Concurrency design

The critical invariant is:

> A seat must never be allocated to two users for the same show.

`Show` protects its seat state with a private lock. The following actions are
atomic per show:

- Check that all seats are `AVAILABLE`
- Mark all seats as `HELD`
- Verify ownership and mark seats as `BOOKED`
- Release seats owned by a session

This prevents the race:

```text
U1 checks A1 as available
U2 checks A1 as available
U1 holds A1
U2 holds A1
```

Only one complete `holdSeats` operation can pass the check-and-update section.

The service synchronizes session maps and session lifecycle transitions. In a
distributed deployment, the per-show lock would need to be replaced or backed by
a database transaction with row-level locking or an atomic conditional update.

## Requested scenarios

The `main` method demonstrates:

1. U1 holds seats and pays successfully; U2 no longer sees them.
2. U1's payment fails with zero retries; seats become available.
3. U1 explicitly closes a session; seats become available.
4. U2 attempts to select an overlapping held seat and receives an error.
5. A session times out and its seats are released.
6. Two concurrent users race to select the same seat; only one succeeds.

## SOLID and extensibility

- **Single Responsibility:** domain objects own state; the service orchestrates use
  cases; payment is behind an interface.
- **Open/Closed:** new payment providers implement `PaymentGateway` without changing
  booking logic.
- **Liskov Substitution:** all payment gateway implementations honor the same
  `PaymentResult` contract.
- **Interface Segregation:** the payment contract is intentionally small.
- **Dependency Inversion:** `MovieBookingService` depends on `PaymentGateway`, not a
  concrete payment provider.

Natural future extensions:

- `BookingRepository` and `ShowRepository` for durable storage
- Pricing and seat-category strategies
- Reservation expiry scheduler
- Payment idempotency keys and refunds
- Notifications
- Distributed locking or transactional seat updates
- Cancellation and booking history

## Trade-offs

### In-memory state

Simple and demo-friendly, but data is lost on restart and cannot coordinate across
application instances.

### Service synchronization

Easy to reason about for an LLD exercise, but it limits throughput and is local to
one JVM. A production deployment should use transactional persistence and avoid
holding a JVM lock around slow external payment calls.

### Payment inside the session lock

This implementation serializes service operations for correctness and clarity.
Production code should reserve seats with a short transaction, call payment outside
the database lock, and finalize the booking using an idempotent payment callback.

### Availability snapshots

`availableSeats()` is only a view. It does not reserve seats. The authoritative
operation is `selectSeats()`, which revalidates availability atomically.
