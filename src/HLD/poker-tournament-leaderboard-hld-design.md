# High-Level Design: Poker Tournament Chip Leaderboard

## Original Problem Statement

Design a leaderboard for a PokerBaazi-like poker tournament app.

The app supports tournaments where:

- Initially the scale is around **2,000 players per tournament**.
- Initially there are around **5 concurrent tournaments**.
- The larger target scale is around **5,000 players per tournament**.
- The larger target scale has around **10 concurrent tournaments**.
- A player can go out of chips.
- Some tournaments allow re-entry after bust-out.
- Leaderboard ranking is based on the number of chips the player currently has.
- Current implementation uses a scheduler that runs periodically, calculates ranks, and saves them in Redis.
- After the tournament ends, final ranks are saved in the database.

This document explains:

1. How the low-scale design can work.
2. Why the current scheduler-based design does not scale well.
3. How to improve the design for larger scale and better freshness.
4. How to correctly handle re-entry and final standings.

---

## 1. Key Design Principle

The key design principle is:

> The poker game engine and tournament database are the source of truth for chip counts. Redis leaderboard is a derived read model optimized for fast rank queries.

Clients should never directly update chip counts or leaderboard ranks.

Correct ownership:

```text
Poker Game Engine
  -> validates hand result
  -> computes chip changes
  -> persists authoritative chip state
  -> emits chip events
  -> leaderboard updates from those events
```

---

## 2. Low-Scale Requirements

Initial scale:

```text
Players per tournament: 2,000
Concurrent tournaments: 5
Total active entries: 10,000
```

At this scale:

- Full leaderboard recomputation is feasible.
- DB queries over active tournament entries are manageable.
- Redis sorted sets are more than enough.
- Operational simplicity may be more important than event-driven complexity.

Low-scale target:

| Requirement | Target |
|---|---:|
| Tournament size | 2,000 players |
| Concurrent tournaments | 5 |
| Total active players | ~10,000 |
| Leaderboard freshness | 5-60 seconds, depending on scheduler |
| Read latency | P95 < 50-100 ms |
| Correctness source | Tournament DB |
| Live leaderboard store | Redis |

---

## 3. Current Low-Scale Scheduler Design

Current design:

```text
Game Engine / Tournament Service
        |
        v
Tournament DB
- tournament_entries
- current_chips
- status
- reentry_count
        |
        v
Scheduler every N seconds/minutes
        |
        v
Query active players ordered by chips
        |
        v
Save leaderboard snapshot in Redis
        |
        v
Leaderboard API reads Redis
```

Example DB query:

```sql
SELECT entry_id, player_id, current_chips, status, reentry_count
FROM tournament_entries
WHERE tournament_id = ?
  AND status = 'ACTIVE'
ORDER BY current_chips DESC;
```

Redis key:

```text
leaderboard:{tournamentId}:active
```

Redis sorted set member:

```text
entryId
```

Redis sorted set score:

```text
current_chips
```

Example:

```text
ZADD leaderboard:tour_123:active 15400 entry_456
```

---

## 4. Why Scheduler Design Works at Low Scale

For the initial scale:

```text
2,000 players/tournament * 5 tournaments = 10,000 active rows
```

Even if the scheduler runs every 5 seconds:

```text
10,000 rows per run
12 runs per minute
120,000 rows per minute
7.2 million rows per hour
```

This can be acceptable with:

- Proper DB indexing.
- Small number of tournaments.
- Atomic Redis writes.
- Low operational complexity.
- Reasonable DB capacity.

Recommended index:

```text
(tournament_id, status, current_chips DESC)
```

The scheduler-based design is acceptable as a **v1** or **low-scale implementation**.

---

## 5. Making the Low-Scale Scheduler Safer

If the scheduler remains the primary leaderboard update mechanism, implement it carefully.

## 5.1 Run per tournament

Do not run one global rebuild blindly.

Better: use a **different scheduler task for each active tournament**.

```text
Tournament Scheduler Coordinator
        |
        v
Find ACTIVE tournaments
        |
        v
Create one rebuild task per tournament
        |
        +-----------------------------+
        |                             |
        v                             v
Rebuild tour_1 leaderboard     Rebuild tour_2 leaderboard
        |                             |
        v                             v
Redis leaderboard:tour_1       Redis leaderboard:tour_2
```

Per-tournament scheduler loop:

```text
Every 5 seconds:
  for each ACTIVE tournament:
      acquire lock: lock:leaderboard:{tournamentId}
      if lock acquired:
          rebuild leaderboard for that tournament
      else:
          skip, another worker is rebuilding it
```

Benefits:

- Failure isolation.
- Parallelism.
- Easier locking.
- Can skip inactive tournaments.
- One slow tournament does not delay all other tournaments.
- Failed rebuilds can be retried only for the affected tournament.
- DB and Redis load is spread across smaller independent jobs.

Why this is better than one global scheduler:

| Global scheduler issue | Per-tournament scheduler benefit |
|---|---|
| One slow tournament delays all others | Each tournament updates independently |
| Partial failure affects the whole batch | Only one tournament is affected |
| Larger DB/Redis spike | Smaller isolated workloads |
| Harder to scale workers | Tournaments can be distributed across workers |
| Harder to pause completed tournaments | Inactive tournaments can be skipped |

## 5.2 Use per-tournament distributed lock

Prevent overlapping scheduler runs:

```text
SET lock:leaderboard:{tournamentId} workerId NX PX 4000
```

If the lock is not acquired, skip that tournament for this run.

This avoids:

- Duplicate work.
- Older rebuild overwriting newer rebuild.
- Redis write races.
- DB load spikes.

## 5.3 Write to temporary Redis key and atomically swap

Do not write directly into the live Redis key.

Bad:

```text
DEL leaderboard:tour_1:active
ZADD player1
ZADD player2
crash
```

This can expose a partial leaderboard.

Better:

```text
leaderboard:{tournamentId}:active:tmp:{runId}
leaderboard:{tournamentId}:active
```

Flow:

```text
1. Build temporary sorted set.
2. Write all active entries to temp key.
3. Set metadata such as lastUpdatedAt and version.
4. Atomically rename temp key to live key.
```

Example:

```text
DEL leaderboard:tour_1:active:tmp:run_123
ZADD leaderboard:tour_1:active:tmp:run_123 9000 entry_1
ZADD leaderboard:tour_1:active:tmp:run_123 8500 entry_2
RENAME leaderboard:tour_1:active:tmp:run_123 leaderboard:tour_1:active
SET leaderboard:tour_1:lastUpdatedAt 2026-09-25T19:35:00Z
```

## 5.4 Skip rebuild if nothing changed

Track tournament leaderboard version:

```text
tournament_leaderboard_version
```

Whenever a hand completes and chip counts change:

```text
version = version + 1
```

Scheduler checks:

```text
if current_version == last_built_version:
    skip rebuild
```

This avoids recomputing unchanged leaderboards.

## 5.5 Use `entryId`, not only `playerId`

Re-entry creates a new tournament life.

So the leaderboard member should be:

```text
entryId
```

not only:

```text
playerId
```

Example:

```text
player_123 entry_1 -> BUSTED
player_123 entry_2 -> ACTIVE after re-entry
```

Active entry mapping:

```text
active_entry:{tournamentId}:{playerId} -> entryId
```

---

## 6. What Happens If Scheduler Runs Every 5 Seconds?

Changing the scheduler from 60 seconds to 5 seconds improves freshness, but it does not change the architecture.

## 6.1 Freshness improvement

With 60-second scheduler:

```text
max staleness ~= 60 seconds
average staleness ~= 30 seconds
```

With 5-second scheduler:

```text
max staleness ~= 5 seconds
average staleness ~= 2.5 seconds
```

This is better for user experience.

## 6.2 Remaining problems

Even with 5 seconds, it still:

- Recomputes unchanged players.
- Creates periodic DB load.
- Can overlap if one run takes more than 5 seconds.
- Requires distributed locks.
- Can expose partial Redis updates if not atomically swapped.
- Does not provide an audit trail by itself.
- Can still be stale after a big hand.
- Does not scale efficiently as tournaments and players increase.

So a 5-second scheduler is an **interim improvement**, not the ideal architecture.

---

## 7. Why Scheduler Design Does Not Support Large Scale Well

Larger target:

```text
Players per tournament: 5,000
Concurrent tournaments: 10
Total active players: 50,000
```

If scheduler runs every 5 seconds:

```text
50,000 rows per run
12 runs per minute
600,000 rows per minute
36 million rows per hour
```

This becomes wasteful because most players may not change chips in every interval.

## 7.1 Periodic DB load spikes

Every scheduler run creates a burst of DB reads and Redis writes.

As tournaments increase, these spikes become larger and harder to control.

## 7.2 Full recomputation is wasteful

If only 100 players changed in the last 5 seconds, scheduler still scans and sorts thousands of players.

Event-driven design updates only changed entries:

```text
ZADD leaderboard:{tournamentId}:active resultingChips entryId
```

## 7.3 Staleness remains

Even 5 seconds can be stale for:

- All-in hands.
- Final table.
- Bubble phase.
- Bust-out.
- Re-entry.

## 7.4 Race conditions become harder

Scheduler may read while a hand update is happening.

Bad sequence:

```text
Player A chips updated
Player B chips not yet updated
Scheduler reads leaderboard
```

The leaderboard can temporarily show inconsistent chip totals if hand updates are not transactionally applied.

## 7.5 Multiple schedulers need coordination

At larger scale, multiple scheduler workers may be needed.

Then you need:

- Per-tournament locks.
- Ownership assignment.
- Retry handling.
- Idempotent writes.
- Failure detection.

This complexity starts to resemble an event-driven system, but with worse freshness and more wasted work.

## 7.6 Redis and DB can drift

If scheduler fails midway or writes partial data, Redis can become stale or inconsistent.

Atomic temp-key swaps reduce this, but do not solve all drift issues.

## 7.7 Poor auditability

Scheduler tells you the current rank, but not why the rank changed.

Poker systems need audit details:

```text
tournamentId
handId
tableId
playerId
entryId
eventId
sequenceNumber
chipDelta
resultingChips
```

This is important for disputes, fraud detection, reconciliation, and compliance.

---

## 8. Improved Large-Scale Design

For larger scale, use an event-driven leaderboard.

```text
Poker Game Engine
        |
        v
DB Transaction
- update tournament_entries.current_chips
- insert chip_events
- insert outbox_events
        |
        v
Outbox Relay
        |
        v
Kafka / Event Bus
partition key = tournamentId
        |
        v
Leaderboard Consumer
        |
        v
Redis Sorted Set
leaderboard:{tournamentId}:active
        |
        v
Leaderboard API
        |
        v
Poker App UI
```

Reconciliation:

```text
Scheduler every 5-10 minutes
        |
        v
Read active entries from DB
        |
        v
Compare/rebuild Redis leaderboard
        |
        v
Alert on mismatch
```

Important change:

> Scheduler becomes a reconciliation mechanism, not the primary leaderboard update path.

---

## 9. Large-Scale Chip Update Flow

After a hand completes:

```text
1. Game Engine computes final chip counts.
2. Game Engine writes chip_events and tournament_entries updates transactionally.
3. Game Engine writes outbox event in the same DB transaction.
4. Outbox relay publishes ChipUpdated event to Kafka.
5. Leaderboard Consumer reads event.
6. Consumer validates idempotency and event order.
7. Consumer updates Redis sorted set.
8. Leaderboard API serves updated ranks from Redis.
```

Example event:

```json
{
  "eventId": "evt_123",
  "tournamentId": "tour_456",
  "handId": "hand_789",
  "tableId": "table_1",
  "sequenceNumber": 10231,
  "entryId": "entry_999",
  "playerId": "player_123",
  "eventType": "HAND_RESULT",
  "chipDelta": 2400,
  "resultingChips": 15400,
  "timestamp": "2026-09-25T19:20:00Z"
}
```

Redis update:

```text
ZADD leaderboard:tour_456:active 15400 entry_999
```

If resulting chips are zero:

```text
ZREM leaderboard:tour_456:active entry_999
```

Recommendation:

> Emit absolute `resultingChips`, not only `chipDelta`, so updates are idempotent and replayable.

---

## 10. Re-entry Design

Re-entry should be modeled as a new tournament entry.

Data model:

```text
tournament_entries(
  entry_id PK,
  tournament_id,
  player_id,
  entry_number,
  status,
  current_chips,
  reentry_count,
  joined_at,
  busted_at,
  eliminated_at,
  updated_at
)
```

Statuses:

```text
REGISTERED
ACTIVE
BUSTED
REENTERED
ELIMINATED
FINISHED
```

Example:

```text
player_123 entry_1 -> BUSTED, chips = 0
player_123 entry_2 -> ACTIVE, chips = 10000
```

Redis active leaderboard:

```text
ZREM leaderboard:tour_1:active entry_1
ZADD leaderboard:tour_1:active 10000 entry_2
```

Why this matters:

- Prevents duplicate active leaderboard entries.
- Preserves re-entry history.
- Keeps final standings auditable.
- Avoids mixing old busted stack with new active stack.

---

## 11. Leaderboard API

## 11.1 Get top K

```http
GET /v1/tournaments/{tournamentId}/leaderboard?limit=100
```

Redis:

```text
ZREVRANGE leaderboard:{tournamentId}:active 0 99 WITHSCORES
```

Response:

```json
{
  "tournamentId": "tour_456",
  "generatedAt": "2026-09-25T19:25:00Z",
  "items": [
    {
      "rank": 1,
      "entryId": "entry_1",
      "playerId": "player_1",
      "displayName": "AceKing",
      "chips": 95000,
      "status": "ACTIVE",
      "reentries": 1
    }
  ]
}
```

## 11.2 Get player rank

```http
GET /v1/tournaments/{tournamentId}/players/{playerId}/rank
```

Flow:

```text
entryId = GET active_entry:{tournamentId}:{playerId}
rank = ZREVRANK leaderboard:{tournamentId}:active entryId
chips = ZSCORE leaderboard:{tournamentId}:active entryId
```

Redis rank is zero-based:

```text
rank = zrevrank + 1
```

## 11.3 Get rank around player

```http
GET /v1/tournaments/{tournamentId}/players/{playerId}/leaderboard-window?before=5&after=5
```

Flow:

```text
rank = ZREVRANK leaderboard:{tournamentId}:active entryId
start = max(0, rank - before)
end = rank + after
ZREVRANGE leaderboard:{tournamentId}:active start end WITHSCORES
```

---

## 12. Kafka Partitioning and Ordering

Kafka topic:

```text
tournament-chip-events
```

Partition key:

```text
tournamentId
```

Why?

- Preserves chip event order per tournament.
- Keeps leaderboard updates sequential per tournament.
- Avoids out-of-order rank updates.

If one tournament becomes too hot:

```text
partition key = tournamentId + tableId
```

Then each event should include:

```text
resultingChips
entryVersion
sequenceNumber
```

Consumer should apply only latest versions.

For current and near-future scale, `tournamentId` partitioning is simpler and sufficient.

---

## 13. Idempotency and Out-of-Order Handling

Each chip event should include:

```text
eventId
tournamentId
entryId
sequenceNumber
resultingChips
```

Idempotency:

```text
processed_event:{eventId}
```

or:

```text
last_processed_seq:{tournamentId}
```

Handling duplicate event:

```text
if eventId already processed:
    ignore
```

Handling older event:

```text
if event.sequenceNumber <= lastProcessedSequence:
    ignore
```

For per-entry protection:

```text
last_entry_version:{tournamentId}:{entryId}
```

Because events contain `resultingChips`, this update is idempotent:

```text
ZADD leaderboard:tour_456:active 15400 entry_999
```

---

## 14. Final Standings After Tournament Ends

The current behavior of saving ranks to DB after the tournament ends is correct, but it should be done carefully.

Important rule:

> Redis is for live leaderboard. Official final rank should be computed from authoritative DB/game state, not from potentially stale Redis.

## 14.1 Separate live leaderboard from final standings

Live leaderboard:

```text
Redis Sorted Set
leaderboard:{tournamentId}:active
```

Final standings:

```text
tournament_standings table
```

## 14.2 Final standings table

```text
tournament_standings(
  tournament_id,
  entry_id,
  player_id,
  finish_position,
  final_chips,
  prize_amount,
  elimination_hand_id,
  eliminated_at,
  reentry_count,
  settlement_status,
  payout_status,
  created_at,
  updated_at,
  PRIMARY KEY(tournament_id, entry_id),
  UNIQUE(tournament_id, finish_position)
)
```

## 14.3 Tournament finalization flow

Use explicit tournament states:

```text
RUNNING -> FINALIZING -> COMPLETED
```

Flow:

```text
Tournament ends
  -> Tournament Service moves status RUNNING -> FINALIZING
  -> acquire tournament finalization lock
  -> ensure all tables/hands are closed
  -> read authoritative tournament_entries from DB
  -> compute final standings
  -> write tournament_standings in DB transaction
  -> mark tournament COMPLETED
  -> emit TournamentFinalized event
  -> cleanup Redis live leaderboard later
```

If something fails:

```text
FINALIZING -> retry safely
```

## 14.4 Use DB lock for finalization

Example:

```sql
SELECT *
FROM tournaments
WHERE tournament_id = ?
FOR UPDATE;
```

This prevents two finalizers from completing the same tournament concurrently.

## 14.5 Assign finish position on elimination if possible

When a player is permanently eliminated:

```text
finish_position = active_players_remaining_after_elimination + 1
```

Benefits:

- Preserves elimination order.
- Supports prize calculation.
- Makes finalization simpler.
- Improves auditability.

At tournament end, finalizer only assigns ranks to remaining players.

---

## 15. Redis Recovery

Redis leaderboard is rebuildable.

If Redis is lost:

```text
1. Read active tournament entries from DB.
2. For each active entry:
      ZADD leaderboard:{tournamentId}:active current_chips entryId
3. Rebuild active_entry mappings.
```

Query:

```sql
SELECT entry_id, player_id, current_chips
FROM tournament_entries
WHERE tournament_id = ?
  AND status = 'ACTIVE';
```

Because DB is source of truth, gameplay correctness does not depend on Redis.

---

## 16. Failure Scenarios

## 16.1 Redis down

Impact:

- Leaderboard unavailable or stale.

Handling:

- Serve last cached snapshot if available.
- Fallback to DB query for small tournaments.
- Rebuild Redis after recovery.
- Gameplay should continue.

DB fallback:

```sql
SELECT *
FROM tournament_entries
WHERE tournament_id = ?
  AND status = 'ACTIVE'
ORDER BY current_chips DESC
LIMIT 100;
```

## 16.2 Kafka lag

Impact:

- Leaderboard stale.

Handling:

- Continue showing last Redis leaderboard with `lastUpdatedAt`.
- Alert if lag exceeds threshold.
- Scale consumers.
- Reconciliation scheduler can repair from DB.

## 16.3 Leaderboard consumer crashes

Handling:

- Restart from last committed Kafka offset.
- Reprocess events idempotently.
- Use `resultingChips` in events.
- Redis updates are idempotent.

## 16.4 Game engine writes DB but fails to publish event

Use outbox pattern:

```text
DB transaction:
  update tournament_entries
  insert chip_event
  insert outbox_event

Outbox relay:
  publish event to Kafka
  mark outbox row published
```

This prevents DB/event inconsistency.

## 16.5 Re-entry race condition

Two re-entry requests for the same busted player arrive.

Handling:

- DB transaction.
- Lock player tournament state.
- Enforce max reentries.
- Use idempotency key for re-entry request/payment.
- Prevent multiple active entries unless tournament explicitly supports multi-entry.

---

## 17. Low Scale vs Large Scale Comparison

| Area | Low-scale design | Large-scale design |
|---|---|---|
| Players | 2k/tournament, 5 tournaments | 5k+ players, 10+ tournaments |
| Ranking update | Scheduler/rebuild acceptable | Event-driven from chip events |
| Redis structure | Sorted set per tournament | Sorted set per tournament, sharded by tournament if needed |
| Source of truth | DB | DB + append-only chip event log |
| Event bus | Optional | Kafka/Pulsar recommended |
| Recovery | Rebuild Redis from DB | Rebuild from DB or replay event log |
| Freshness | 5-60 seconds | Sub-second to 1 second |
| Re-entry | Must model entryId carefully | Must model entry lifecycle and idempotency |
| Scaling | Simple cron okay | Partition by tournamentId |
| Failure handling | Manual retry acceptable | Outbox, idempotency, replay |
| Scheduler role | Primary update path | Reconciliation/repair only |

---

## 18. Suggested Migration Plan

## Phase 1: Improve current scheduler

- Run a separate scheduler task per active tournament instead of one global rebuild.
- Add per-tournament distributed lock.
- Use Redis sorted set per tournament.
- Use temp Redis key and atomic rename.
- Add `lastUpdatedAt`.
- Add DB index:
  ```text
  (tournament_id, status, current_chips DESC)
  ```
- Use `entryId` as Redis member.
- Add `active_entry:{tournamentId}:{playerId}` mapping.
- Skip inactive, paused, completed, or unchanged tournaments.

## Phase 2: Add dirty-version optimization

- Increment tournament leaderboard version after hand completion.
- Scheduler skips unchanged tournaments.
- Reduce unnecessary rebuilds.

## Phase 3: Add event-driven Redis updates

- After each hand, update Redis for changed entries.
- Keep scheduler every 5-10 minutes for reconciliation only.

## Phase 4: Add outbox and Kafka

- Write chip events and outbox events transactionally.
- Publish to Kafka.
- Leaderboard consumer updates Redis.
- Use idempotency and sequence numbers.

## Phase 5: Add real-time push

- WebSocket/SSE for live leaderboard.
- Batch push updates every 1 second.
- Polling can remain fallback.

---

## 19. Recommended Final Architecture

```text
Poker Game Engine
        |
        v
DB Transaction
- update tournament_entries.current_chips
- insert chip_events
- insert outbox_events
        |
        v
Outbox Relay
        |
        v
Kafka / Event Bus
partition key = tournamentId
        |
        v
Leaderboard Consumer
        |
        v
Redis Sorted Set
leaderboard:{tournamentId}:active
        |
        v
Leaderboard API
        |
        v
Poker App UI
```

Reconciliation:

```text
Scheduler every 5-10 minutes
        |
        v
Read active entries from DB
        |
        v
Compare/rebuild Redis leaderboard
        |
        v
Alert on mismatch
```

Tournament finalization:

```text
Tournament ends
  -> RUNNING -> FINALIZING
  -> acquire finalization lock
  -> verify all hands closed
  -> compute standings from DB
  -> save tournament_standings
  -> FINALIZING -> COMPLETED
```

---

## 20. Senior-Level Closing Statement

A strong interview summary:

> For the initial scale of 2,000 players per tournament and 5 concurrent tournaments, a scheduler that recomputes leaderboards and saves them in Redis can work as a simple low-scale design. If we keep this approach, it should use Redis sorted sets, per-tournament locks, atomic temp-key swaps, proper indexes, and `entryId` rather than `playerId` to support re-entry correctly. However, the scheduler design does not scale well because it repeatedly scans and sorts unchanged players, creates periodic DB load spikes, remains stale between runs, and needs complex coordination as the system grows. For larger scale, I would move to an event-driven design where the game engine writes authoritative chip state and chip events transactionally, an outbox publishes those events to Kafka partitioned by tournamentId, and a leaderboard consumer updates Redis sorted sets using absolute `resultingChips`. The scheduler should become a reconciliation job, not the primary update path. Final official ranks should be saved to DB through a dedicated tournament finalization workflow using authoritative DB state, not from potentially stale Redis.
