# Movie Ticket Booking Database Schema

## 1. Scope and assumptions

The schema supports:

- Cities, theatres, screens, and physical seats
- Movies and shows
- Per-show seat inventory
- Temporary seat holds
- Bookings and booking seats
- Payments and idempotent payment callbacks
- Concurrent booking requests
- Expiration of abandoned holds

The examples use PostgreSQL syntax. The same model can be implemented in other
relational databases with equivalent row-locking and constraint features.

## 2. Core relationships

```text
City
  └── Theatre
        └── Screen
              └── Seat

Movie ───< Show >─── Screen
Show  ───< ShowSeat
User  ───< Booking ───< BookingSeat >─── ShowSeat
Booking ───< Payment
```

`Seat` is a physical seat on a screen. `ShowSeat` is the seat's state for one
specific show. This distinction is important because seat A1 can be booked for
one show while remaining available for another show.

## 3. Tables

```sql
CREATE TABLE app_user (
    user_id         UUID PRIMARY KEY,
    display_name    VARCHAR(200) NOT NULL,
    email           VARCHAR(320) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE city (
    city_id         BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    state_name      VARCHAR(120),
    country_code    CHAR(2) NOT NULL,
    UNIQUE (name, state_name, country_code)
);

CREATE TABLE theatre (
    theatre_id      BIGSERIAL PRIMARY KEY,
    city_id         BIGINT NOT NULL REFERENCES city(city_id),
    name            VARCHAR(200) NOT NULL,
    address         TEXT NOT NULL,
    timezone        VARCHAR(80) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (city_id, name)
);

CREATE TABLE screen (
    screen_id       BIGSERIAL PRIMARY KEY,
    theatre_id      BIGINT NOT NULL REFERENCES theatre(theatre_id),
    name            VARCHAR(100) NOT NULL,
    capacity        INTEGER NOT NULL CHECK (capacity > 0),
    UNIQUE (theatre_id, name)
);

CREATE TABLE seat (
    seat_id         BIGSERIAL PRIMARY KEY,
    screen_id       BIGINT NOT NULL REFERENCES screen(screen_id),
    row_label       VARCHAR(10) NOT NULL,
    seat_number     INTEGER NOT NULL CHECK (seat_number > 0),
    seat_type       VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    UNIQUE (screen_id, row_label, seat_number)
);

CREATE TABLE movie (
    movie_id        BIGSERIAL PRIMARY KEY,
    title           VARCHAR(300) NOT NULL,
    duration_sec    INTEGER NOT NULL CHECK (duration_sec > 0),
    language_code   VARCHAR(10),
    release_date    DATE,
    active          BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE show (
    show_id         BIGSERIAL PRIMARY KEY,
    movie_id        BIGINT NOT NULL REFERENCES movie(movie_id),
    screen_id       BIGINT NOT NULL REFERENCES screen(screen_id),
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    CHECK (ends_at > starts_at),
    CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED'))
);

CREATE TABLE show_seat (
    show_seat_id    BIGSERIAL PRIMARY KEY,
    show_id         BIGINT NOT NULL REFERENCES show(show_id) ON DELETE CASCADE,
    seat_id         BIGINT NOT NULL REFERENCES seat(seat_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    hold_token      UUID,
    hold_expires_at TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (show_id, seat_id),
    CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    CHECK (
        (status = 'HELD' AND hold_token IS NOT NULL AND hold_expires_at IS NOT NULL)
        OR status <> 'HELD'
    )
);

CREATE TABLE booking (
    booking_id      UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES app_user(user_id),
    show_id         BIGINT NOT NULL REFERENCES show(show_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    hold_token      UUID NOT NULL UNIQUE,
    total_amount    NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    currency        CHAR(3) NOT NULL,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMPTZ,
    CHECK (status IN (
        'PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_FAILED',
        'CANCELLED', 'EXPIRED'
    ))
);

CREATE TABLE booking_seat (
    booking_id      UUID NOT NULL REFERENCES booking(booking_id),
    show_seat_id    BIGINT NOT NULL REFERENCES show_seat(show_seat_id),
    price           NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    PRIMARY KEY (booking_id, show_seat_id),
    UNIQUE (show_seat_id)
);

CREATE TABLE payment (
    payment_id          UUID PRIMARY KEY,
    booking_id          UUID NOT NULL REFERENCES booking(booking_id),
    provider            VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(200),
    idempotency_key     VARCHAR(200) NOT NULL UNIQUE,
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency            CHAR(3) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_payment_id),
    CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED', 'REFUNDED'))
);
```

## 4. Important indexes

```sql
CREATE INDEX idx_theatre_city_active
    ON theatre(city_id) WHERE active = true;

CREATE INDEX idx_show_search
    ON show(movie_id, starts_at, status);

CREATE INDEX idx_show_screen_time
    ON show(screen_id, starts_at);

CREATE INDEX idx_available_show_seats
    ON show_seat(show_id, seat_id)
    WHERE status = 'AVAILABLE';

CREATE INDEX idx_expiring_show_seats
    ON show_seat(hold_expires_at)
    WHERE status = 'HELD';

CREATE INDEX idx_user_bookings
    ON booking(user_id, created_at DESC);

CREATE INDEX idx_show_bookings
    ON booking(show_id, status);

CREATE INDEX idx_expiring_bookings
    ON booking(expires_at)
    WHERE status = 'PENDING_PAYMENT';
```

## 5. Show creation

When a show is created, populate its seat inventory from the screen:

```sql
INSERT INTO show_seat (show_id, seat_id)
SELECT :show_id, seat_id
FROM seat
WHERE screen_id = :screen_id;
```

The `UNIQUE (show_id, seat_id)` constraint prevents duplicate inventory rows.
The application should also verify that `show.screen_id` matches the selected
screen and that the screen belongs to the expected theatre.

## 6. Concurrent seat booking

The database must be the final authority. A caller may first display available
seats, but that result is only a snapshot. The hold operation must atomically
revalidate and update the rows.

### Recommended transaction

The application generates one `hold_token` and one `booking_id` for the request.
It then executes:

```sql
BEGIN;

-- Lock and validate every requested seat in a deterministic order.
SELECT show_seat_id, status, hold_token, hold_expires_at
FROM show_seat
WHERE show_id = :show_id
  AND seat_id = ANY(:seat_ids)
ORDER BY show_seat_id
FOR UPDATE;
```

The application verifies that:

1. Every requested seat exists.
2. Every requested seat is `AVAILABLE`, or is `HELD` but its hold has expired.
3. The request does not exceed the configured seat limit.

It then releases expired holds and claims the seats:

```sql
UPDATE show_seat
SET status = 'HELD',
    hold_token = :hold_token,
    hold_expires_at = :hold_expires_at,
    version = version + 1
WHERE show_id = :show_id
  AND seat_id = ANY(:seat_ids);

INSERT INTO booking (
    booking_id, user_id, show_id, status, hold_token,
    total_amount, currency, expires_at
)
VALUES (
    :booking_id, :user_id, :show_id, 'PENDING_PAYMENT', :hold_token,
    :total_amount, :currency, :hold_expires_at
);

INSERT INTO booking_seat (booking_id, show_seat_id, price)
SELECT :booking_id, show_seat_id, price_for_seat
FROM requested_seats;

COMMIT;
```

The application should build the `requested_seats` values after locking the
rows. In a real implementation, price should come from a show-specific pricing
table rather than being trusted from the client.

### Why this prevents double booking

Suppose U1 and U2 request the same seat:

1. U1 locks the `show_seat` row first.
2. U2 waits on the same row.
3. U1 changes it to `HELD` and commits.
4. U2 obtains the lock, sees `HELD`, and fails.

`FOR UPDATE` serializes conflicting claims. `booking_seat.show_seat_id` being
unique provides an additional database invariant: one show seat cannot belong to
two bookings.

Always lock requested seats in ascending `show_seat_id` order. This reduces
deadlock risk when two requests contain overlapping groups of seats.

## 7. Confirming payment

Payment should use an idempotency key because providers can retry callbacks.

```sql
BEGIN;

SELECT status, hold_token
FROM booking
WHERE booking_id = :booking_id
FOR UPDATE;

-- Confirm only the booking that still owns the active hold.
UPDATE booking
SET status = 'CONFIRMED',
    confirmed_at = now(),
    expires_at = NULL
WHERE booking_id = :booking_id
  AND status = 'PENDING_PAYMENT'
  AND hold_token = :hold_token;

UPDATE show_seat
SET status = 'BOOKED',
    hold_token = NULL,
    hold_expires_at = NULL,
    version = version + 1
WHERE show_seat_id IN (
    SELECT show_seat_id
    FROM booking_seat
    WHERE booking_id = :booking_id
)
AND status = 'HELD'
AND hold_token = :hold_token;

INSERT INTO payment (
    payment_id, booking_id, provider, provider_payment_id,
    idempotency_key, amount, currency, status
)
VALUES (
    :payment_id, :booking_id, :provider, :provider_payment_id,
    :idempotency_key, :amount, :currency, 'SUCCESS'
)
ON CONFLICT (idempotency_key) DO NOTHING;

COMMIT;
```

The payment callback must not confirm an expired, cancelled, or already
confirmed booking. If payment succeeds after the hold has expired, the system
should initiate a refund or mark the payment for reconciliation; it must not
assign the seats again.

## 8. Payment failure and cancellation

For a retryable payment failure:

```sql
UPDATE payment
SET status = 'FAILED',
    failure_reason = :reason,
    updated_at = now()
WHERE idempotency_key = :idempotency_key;
```

When retries are exhausted or the user explicitly cancels:

```sql
BEGIN;

SELECT hold_token, status
FROM booking
WHERE booking_id = :booking_id
FOR UPDATE;

UPDATE show_seat
SET status = 'AVAILABLE',
    hold_token = NULL,
    hold_expires_at = NULL,
    version = version + 1
WHERE show_seat_id IN (
    SELECT show_seat_id FROM booking_seat WHERE booking_id = :booking_id
)
AND status = 'HELD'
AND hold_token = :hold_token;

UPDATE booking
SET status = CASE
        WHEN :expired THEN 'EXPIRED'
        ELSE 'CANCELLED'
    END,
    expires_at = NULL
WHERE booking_id = :booking_id
  AND status = 'PENDING_PAYMENT';

COMMIT;
```

## 9. Hold expiration

Use a scheduled worker to find expired holds in batches:

```sql
SELECT DISTINCT hold_token
FROM show_seat
WHERE status = 'HELD'
  AND hold_expires_at <= now()
ORDER BY hold_expires_at
FOR UPDATE SKIP LOCKED
LIMIT 500;
```

For each token, release the seats and expire the booking in one transaction.
`SKIP LOCKED` lets multiple workers process different holds without waiting on
one another.

The booking operation should also treat an expired hold as unavailable until it
successfully claims it. Expiration must not depend only on the background worker.

## 10. Available seats query

```sql
SELECT s.show_seat_id, s.seat_id, se.row_label, se.seat_number, se.seat_type
FROM show_seat s
JOIN seat se ON se.seat_id = s.seat_id
WHERE s.show_id = :show_id
  AND (
      s.status = 'AVAILABLE'
      OR (s.status = 'HELD' AND s.hold_expires_at <= now())
  )
ORDER BY se.row_label, se.seat_number;
```

This query is informational. The hold transaction must recheck the state under
row locks.

## 11. State transitions

### Show seat

```text
AVAILABLE -> HELD
HELD      -> AVAILABLE       (cancel, payment failure, timeout)
HELD      -> BOOKED          (successful payment)
```

There is no direct `AVAILABLE -> BOOKED` transition because every booking must
have a temporary hold before payment.

### Booking

```text
PENDING_PAYMENT -> CONFIRMED
PENDING_PAYMENT -> PAYMENT_FAILED
PENDING_PAYMENT -> CANCELLED
PENDING_PAYMENT -> EXPIRED
```

An already terminal booking is immutable except for separate refund state in
`payment`.

## 12. Preventing invalid data

- Foreign keys prevent orphaned theatres, shows, seats, and bookings.
- Unique theatre/screen and screen/seat keys prevent duplicate names and seats.
- `UNIQUE (show_id, seat_id)` prevents duplicate show inventory.
- `UNIQUE (show_seat_id)` in `booking_seat` prevents double booking.
- Check constraints restrict state values and invalid amounts.
- Server-generated IDs, timestamps, prices, and hold tokens must not be trusted
  from the client.

## 13. Scaling considerations

### Read-heavy show discovery

Cache movie, theatre, screen, and show metadata. Keep availability reads
uncached or use short-lived caches because seat state changes frequently.

### Partitioning

For very large deployments, partition operational tables by show date or
theatre/city. `show_seat` is the primary high-volume table and is a candidate
for partitioning by show date through a relationship to `show`.

### Hot shows

A popular show creates contention on its `show_seat` rows. Mitigations include:

- Keep transactions short.
- Lock only requested seats, not the entire show.
- Lock requested seats in deterministic order.
- Avoid external payment calls inside the database transaction.
- Use a bounded hold duration.
- Partition or shard shows across database instances when necessary.

### Read replicas

Use primary storage for seat availability immediately before holding seats.
Read replicas can serve catalogue and movie-search queries, but stale replica
data must never authorize a booking.

## 14. Production payment boundary

Do not keep database locks while calling a payment provider. The recommended
flow is:

1. Transactionally create a short-lived hold and `PENDING_PAYMENT` booking.
2. Commit.
3. Call the payment provider with an idempotency key.
4. Process the provider callback transactionally.
5. Confirm only if the hold token is still valid.
6. Refund or reconcile late payment success after expiry.

This keeps database locks short while preserving correctness through ownership,
state checks, idempotency, and reconciliation.
