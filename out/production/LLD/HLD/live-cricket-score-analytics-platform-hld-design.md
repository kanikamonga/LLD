# High-Level Design: Live Cricket Score and Analytics Platform like Cricbuzz

## Original Problem Statement

Design the high-level architecture for a platform like Cricbuzz, which provides live cricket scores, commentary, and analytics to millions of users.

Focus areas:

- Real-time data ingestion.
- Event processing.
- Live score storage.
- Commentary and analytics.
- Low-latency APIs.
- WebSocket/SSE live updates.
- Caching.
- Scalability during peak matches.
- Fault tolerance and reliability.

---

## 1. Problem Statement

Design a platform that provides:

- Live cricket scores.
- Ball-by-ball commentary.
- Match events: wickets, boundaries, reviews, milestones.
- Scorecards.
- Match schedules and results.
- Player/team statistics.
- Live analytics and insights.
- Push updates to millions of users during popular matches.

Core challenge:

> Real-time match data must be ingested, validated, processed, stored, cached, and delivered to millions of users with very low latency.

---

## 2. Requirements

### Functional requirements

1. **Live score updates**
   - Current score.
   - Overs.
   - Batsmen/bowlers.
   - Run rate.
   - Required run rate.
   - Partnership.
   - Extras.
   - Fall of wickets.

2. **Ball-by-ball commentary**
   - Delivery event.
   - Text commentary.
   - Event type: run, boundary, wicket, no-ball, wide, review, etc.

3. **Scorecard**
   - Batting scorecard.
   - Bowling scorecard.
   - Fall of wickets.
   - Partnerships.
   - Extras.

4. **Match metadata**
   - Teams.
   - Venue.
   - Toss.
   - Playing XI.
   - Umpires.
   - Match status.
   - Match format.

5. **Schedule and results**
   - Upcoming matches.
   - Live matches.
   - Completed matches.
   - Series/tournament pages.

6. **Analytics**
   - Worm graph.
   - Manhattan chart.
   - Win probability.
   - Player stats.
   - Head-to-head stats.
   - Team rankings.
   - Historical performance.

7. **Real-time delivery**
   - Push live updates to mobile/web clients.
   - Support millions of subscribers during high-profile matches.

8. **Notifications**
   - Wicket alerts.
   - Match start.
   - Innings break.
   - Result.
   - Milestones.

### Non-functional requirements

| Requirement | Target |
|---|---:|
| Live update latency | P95 < 1 second from ingestion to client |
| API read latency | P95 < 100-200 ms |
| Availability | 99.99% |
| Peak concurrent users | Millions during major matches |
| Data correctness | Strong for official score state |
| Commentary consistency | Ordered per match/innings/ball |
| Historical analytics | Eventually consistent |
| Scalability | Horizontally scalable |
| Fault tolerance | No data loss for confirmed match events |

---

## 3. Key Design Principle

The most important design principle:

> Match event ingestion is the source of truth. Live score, commentary feed, scorecard, analytics, cache, and push updates are all derived views from ordered match events.

Instead of independently updating many serving systems, ingest structured events:

```text
BallStarted
RunsScored
Boundary
Wicket
OverCompleted
ReviewTaken
InningsCompleted
MatchCompleted
CommentaryAdded
CorrectionApplied
```

---

## 4. High-Level Architecture

![Live Cricket Platform Architecture](../../docs/live-cricket-platform-architecture.png)

PNG source: `docs/live-cricket-platform-architecture.png`

```text
Official Data Providers / Scorers
        |
        v
Data Ingestion Gateway
        |
        v
Validation + Normalization Service
        |
        v
Kafka / Event Streaming Platform
        |
        +----------------------+----------------------+----------------------+
        |                      |                      |
        v                      v                      v
Live Match Processor     Commentary Processor    Analytics Processor
        |                      |                      |
        v                      v                      v
Live State Store         Commentary Store         Analytics Store
        |                      |                      |
        +----------+-----------+----------+-----------+
                   |                      |
                   v                      v
              Redis Cache          Historical DB / Warehouse
                   |
                   v
          API Gateway / Feed Service
                   |
          +--------+---------+
          |                  |
          v                  v
 Web / Mobile APIs     WebSocket/SSE Gateway
          |                  |
          v                  v
       Clients          Live Subscribers
```

Notification side:

```text
Kafka Events
    |
    v
Notification Service
    |
    +------------------+
    |                  |
    v                  v
Push Notifications   Email/SMS/WhatsApp if needed
```

---

## 5. Data Ingestion

### 5.1 Sources

Live match data can come from:

1. **Official scoring providers**
   - ICC/BCCI/vendor feed.
   - Structured ball-by-ball APIs.
   - Webhooks.
   - Streaming feed.

2. **Manual scorers/admin dashboard**
   - Backup when provider feed fails.
   - Human operator enters events.

3. **Commentary editors**
   - Text commentary.
   - Expert notes.
   - Match insights.

4. **Automated analytics systems**
   - Win probability.
   - Player matchup stats.
   - Predictive models.

### 5.2 Ingestion Gateway

Responsibilities:

- Authenticate provider/scorer.
- Validate schema.
- Deduplicate events.
- Add ingestion timestamp.
- Assign source priority.
- Publish raw events to Kafka.

Example raw event:

```json
{
  "eventId": "evt_123",
  "matchId": "match_456",
  "innings": 1,
  "over": 12,
  "ball": 4,
  "eventType": "BALL_RESULT",
  "battingTeamId": "team_ind",
  "bowlingTeamId": "team_aus",
  "batsmanId": "player_rohit",
  "bowlerId": "player_starc",
  "runs": 4,
  "extras": 0,
  "wicket": false,
  "timestamp": "2026-09-30T06:30:00Z",
  "source": "official_provider"
}
```

### 5.3 Event ordering

Ordering is critical per match.

Kafka topic:

```text
match-events
```

Partition key:

```text
matchId
```

Why:

- Ensures events for one match are processed in order.
- Different matches can be processed in parallel.
- Prevents ball `12.5` being applied before `12.4` for the same match.

---

## 6. Data Processing

### 6.1 Validation + Normalization Service

Responsibilities:

- Validate event schema.
- Normalize provider-specific fields.
- Convert to canonical event format.
- Check duplicate `eventId`.
- Validate match state transition.
- Reject impossible events.

Examples of invalid events:

```text
ball 12.4 arrives after over 13 already completed
wicket count > 10
innings updated after match completed
negative score
same ball applied twice
```

Canonical event:

```json
{
  "eventId": "evt_123",
  "matchId": "match_456",
  "sequenceNumber": 782,
  "eventType": "BALL_RESULT",
  "innings": 1,
  "deliveryId": "1.12.4",
  "runs": 4,
  "extras": 0,
  "wicket": null,
  "players": {
    "striker": "player_rohit",
    "nonStriker": "player_kohli",
    "bowler": "player_starc"
  },
  "createdAt": "2026-09-30T06:30:00Z"
}
```

### 6.2 Live Match Processor

Consumes canonical match events and updates live match state.

Responsibilities:

- Maintain current score.
- Update batsman/bowler stats.
- Update over state.
- Update innings status.
- Update required run rate.
- Update current partnership.
- Update match status.
- Emit `LiveStateUpdated`.

Live state example:

```json
{
  "matchId": "match_456",
  "status": "LIVE",
  "innings": 1,
  "battingTeam": "IND",
  "bowlingTeam": "AUS",
  "score": {
    "runs": 145,
    "wickets": 3,
    "overs": "16.4"
  },
  "batsmen": [
    {
      "playerId": "rohit",
      "runs": 65,
      "balls": 42
    }
  ],
  "bowler": {
    "playerId": "starc",
    "overs": "3.4",
    "runs": 32,
    "wickets": 1
  },
  "runRate": 8.7,
  "lastUpdatedSeq": 782
}
```

### 6.3 Commentary Processor

Responsibilities:

- Store ball-by-ball commentary.
- Ensure ordering by match/innings/ball/sequence.
- Attach text commentary to score event.
- Support multiple languages if needed.
- Emit commentary updates to clients.

### 6.4 Scorecard Processor

Builds scorecard from events:

- Batting scorecard.
- Bowling scorecard.
- Fall of wickets.
- Partnerships.
- Extras.
- Team innings summary.

### 6.5 Analytics Processor

Computes:

- Win probability.
- Projected score.
- Required run rate trend.
- Worm graph.
- Manhattan chart.
- Player matchup stats.
- Phase analysis:
  - Powerplay.
  - Middle overs.
  - Death overs.

Analytics can be eventually consistent with official score state.

---

## 7. Storage Design

### 7.1 Match Metadata DB

Use PostgreSQL/MySQL.

```text
matches(
  match_id PK,
  series_id,
  venue_id,
  team_a,
  team_b,
  start_time,
  format,
  status,
  toss_result,
  created_at,
  updated_at
)
```

```text
teams(
  team_id PK,
  name,
  short_name,
  country
)
```

```text
players(
  player_id PK,
  name,
  team_id,
  role
)
```

Why relational:

- Structured metadata.
- Joins are useful.
- Strong consistency is useful for fixtures and official metadata.

### 7.2 Live State Store

Use Redis or DynamoDB/Cassandra.

For ultra-low-latency live state:

```text
live_match:{matchId}
scorecard:{matchId}
latest_commentary:{matchId}
```

Redis is ideal because:

- Very high read volume.
- Low latency.
- Live data is small.
- Can be rebuilt from event log.

### 7.3 Match Event Log

Use Kafka for streaming and object storage/data lake for durable archive.

Also persist canonical events in an event store:

```text
match_events(
  match_id,
  sequence_number,
  event_id,
  event_type,
  innings,
  delivery_id,
  payload,
  created_at,
  PRIMARY KEY(match_id, sequence_number)
)
```

Purpose:

- Replay match state.
- Audit scoring corrections.
- Rebuild scorecard.
- Historical analysis.

### 7.4 Commentary Store

Use Cassandra/DynamoDB or PostgreSQL partitioned by match.

```text
commentary(
  match_id,
  sequence_number,
  innings,
  delivery_id,
  text,
  tags,
  created_at,
  PRIMARY KEY(match_id, sequence_number)
)
```

Access pattern:

```text
get latest commentary for match
get commentary page by cursor
```

Partition key:

```text
matchId
```

Sort key:

```text
sequenceNumber DESC
```

### 7.5 Historical Analytics Store

Use data lake + warehouse.

Examples:

- S3/GCS + Spark.
- BigQuery/Snowflake/Redshift.
- ClickHouse/Druid/Pinot for fast analytical queries.

### 7.6 User Profile Store

For personalization and notifications.

```text
user_profiles(
  user_id,
  favorite_teams,
  favorite_players,
  notification_preferences,
  locale,
  timezone
)
```

---

## 8. API Design

### 8.1 Get Live Match Summary

```http
GET /v1/matches/{matchId}/live
```

Read path:

```text
API -> Redis live_match:{matchId}
```

Response:

```json
{
  "matchId": "match_456",
  "status": "LIVE",
  "teams": {
    "batting": "IND",
    "bowling": "AUS"
  },
  "score": {
    "runs": 145,
    "wickets": 3,
    "overs": "16.4"
  },
  "runRate": 8.7,
  "lastUpdatedSeq": 782
}
```

### 8.2 Get Scorecard

```http
GET /v1/matches/{matchId}/scorecard
```

Response includes:

- Batting card.
- Bowling card.
- Fall of wickets.
- Partnerships.
- Extras.

### 8.3 Get Commentary

```http
GET /v1/matches/{matchId}/commentary?limit=20&cursor=...
```

### 8.4 Get Match List

```http
GET /v1/matches?status=live
GET /v1/matches?date=2026-09-30
GET /v1/series/{seriesId}/matches
```

Use cache heavily because match lists are read frequently.

### 8.5 Subscribe to Live Match Updates

WebSocket:

```text
WS /v1/live
```

Client message:

```json
{
  "type": "SUBSCRIBE_MATCH",
  "matchId": "match_456"
}
```

Server update:

```json
{
  "type": "LIVE_SCORE_UPDATE",
  "matchId": "match_456",
  "sequenceNumber": 783,
  "score": {
    "runs": 149,
    "wickets": 3,
    "overs": "16.5"
  },
  "event": {
    "type": "FOUR",
    "text": "Boundary through midwicket"
  }
}
```

---

## 9. Real-Time Delivery

![Live Cricket Update Flow](../../docs/live-cricket-update-flow.png)

PNG source: `docs/live-cricket-update-flow.png`

### 9.1 WebSockets

Best for:

- Live score pages.
- Mobile app live match screen.
- High-frequency updates.

Architecture:

```text
Kafka live updates
      |
      v
Realtime Fanout Service
      |
      v
WebSocket Gateway Fleet
      |
      v
Connected Clients
```

### 9.2 Server-Sent Events

Good for web clients requiring one-way updates.

Pros:

- Simpler than WebSockets.
- Works well for server-to-client updates.

Cons:

- One-way only.
- Less flexible for bidirectional subscription changes.

### 9.3 Polling / Long Polling

Fallback:

```http
GET /v1/matches/{matchId}/live?sinceSeq=782
```

Recommended:

> Use WebSocket/SSE for active live match pages, REST polling fallback, and push notifications for important events.

---

## 10. WebSocket Fanout Scaling

During a major match, millions of users may subscribe.

Fanout architecture:

```text
Live Match Processor
       |
       v
Kafka topic: live-match-updates
       |
       v
Realtime Fanout Service
       |
       v
Regional WebSocket Gateways
       |
       v
Clients
```

Partition key:

```text
matchId
```

Gateway maintains:

```text
matchId -> connected client sessions
```

When update arrives:

```text
broadcast to all sessions subscribed to matchId
```

### 10.1 Backpressure

If clients are slow:

- Drop intermediate updates and send latest state.
- Use sequence numbers.
- Limit per-connection buffer.
- Disconnect extremely slow clients.
- Send compact delta updates.

Example:

```text
If client misses seq 783, 784, 785:
  send latest live state snapshot at seq 785
```

For cricket, latest score is more important than delivering every intermediate update to every client.

---

## 11. Caching Strategy

### 11.1 CDN / Edge Cache

Cache:

- Static assets.
- Player images.
- Team logos.
- News images.
- Match schedules.
- Completed scorecards.
- Historical stats.

Do not heavily cache rapidly changing live score unless TTL is very short.

### 11.2 Redis Cache

Cache hot live data:

```text
live_match:{matchId}
scorecard:{matchId}
latest_commentary:{matchId}
match_list:live
series:{seriesId}:matches
```

TTL:

```text
live_match: short TTL or explicitly updated
scorecard: updated by processor
commentary: updated by processor
match list: 5-30 seconds
```

### 11.3 Client Cache

Client should track:

```text
lastSequenceNumber
```

Then request only newer updates:

```http
GET /v1/matches/{matchId}/updates?sinceSeq=782
```

---

## 12. Handling Corrections

Cricket scoring corrections happen:

- Runs corrected.
- Wicket changed.
- No-ball/wide adjusted.
- Batsman/bowler attribution fixed.

Correction event:

```json
{
  "eventType": "CORRECTION",
  "matchId": "match_456",
  "correctionId": "corr_123",
  "targetSequenceNumber": 782,
  "correctedPayload": {
    "runs": 3,
    "extras": 1
  }
}
```

Processing:

```text
1. Mark original event corrected.
2. Apply correction event.
3. Recompute affected live state from checkpoint or event log.
4. Update Redis.
5. Push correction update to clients.
```

Important:

> Event log should be append-only. Do not silently mutate history without audit.

---

## 13. Data Consistency Model

| Area | Consistency | Reason |
|---|---|---|
| Official score state | Strong per match event sequence | Score correctness matters |
| Live cache | Eventually consistent with event processor | Cache can lag slightly |
| Commentary | Ordered per match | User expects correct ball order |
| Analytics | Eventually consistent | Can lag live score |
| Notifications | At-least-once | Duplicate prevention needed |
| Historical stats | Eventually consistent | Batch recomputation acceptable |
| WebSocket delivery | Best effort with sequence recovery | Clients can recover via REST |

---

## 14. Scalability Strategies

### 14.1 Scale ingestion by match

Partition Kafka by:

```text
matchId
```

Different matches process independently.

### 14.2 Scale reads with cache

Live score read path should hit Redis, not DB:

```text
Client -> API -> Redis
```

### 14.3 Scale real-time fanout horizontally

WebSocket gateways scale by:

```text
number of active connections
```

Fanout service scales by:

```text
matches and update volume
```

### 14.4 Scale hot matches

For a World Cup final:

- Use regional WebSocket gateways.
- Use Redis/local memory cache.
- Send compact updates.
- Use CDN for non-live assets.
- Use adaptive update batching.
- Allow polling fallback.
- Use separate hot-match fanout cluster if needed.

---

## 15. Notification System

Events that trigger notifications:

```text
MatchStarted
Wicket
Fifty
Century
InningsBreak
MatchResult
SuperOver
```

Notification flow:

```text
Kafka match events
      |
      v
Notification Rules Engine
      |
      v
User Preference Service
      |
      v
Push Notification Service
      |
      v
APNS / FCM
```

Dedup key:

```text
userId + matchId + eventType + sequenceNumber
```

Use at-least-once delivery with deduplication.

---

## 16. Fault Tolerance

### 16.1 Ingestion provider failure

Handling:

- Switch to secondary provider.
- Use manual scoring dashboard.
- Mark source as degraded.
- Alert operations team.

### 16.2 Kafka lag

Impact:

- Live score updates delayed.

Handling:

- Show last updated timestamp.
- Scale consumers.
- Prioritize live matches.
- Alert if lag exceeds threshold.

### 16.3 Redis failure

Handling:

- Fallback to live state store or DB for smaller traffic.
- Rebuild Redis from event processor state.
- Use Redis cluster with replicas.

### 16.4 WebSocket gateway failure

Handling:

- Clients reconnect.
- Client sends `lastSequenceNumber`.
- Server sends missed updates or latest snapshot.

### 16.5 Processor crash

Handling:

- Resume from Kafka offset.
- Rebuild state from last checkpoint + event log.
- Idempotent event processing.

---

## 17. Observability

Metrics:

```text
ingestion_events_per_second
provider_event_delay
validation_error_rate
kafka_consumer_lag
live_update_processing_latency
redis_read_latency
redis_write_latency
api_latency_p95_p99
websocket_active_connections
websocket_messages_per_second
fanout_latency
dropped_updates_count
correction_event_count
notification_delivery_rate
```

Alerts:

- Live update latency > SLA.
- Kafka lag.
- Provider feed down.
- Redis errors.
- WebSocket reconnect spike.
- API error spike.
- Data validation failures.
- Score correction spike.

Logs/traces should include:

```text
matchId
eventId
sequenceNumber
innings
deliveryId
source
processor
latency
errorCode
```

---

## 18. Security

- Authenticate scorers/providers.
- Authorize admin scoring dashboard.
- TLS everywhere.
- Validate event payloads.
- Rate-limit public APIs.
- Protect internal scoring APIs.
- Audit all manual corrections.
- Prevent client-side score injection.
- Encrypt user profile data.
- Respect notification preferences.

---

## 19. Disaster Recovery

- Kafka replicated across AZs.
- Redis cluster with replicas.
- Metadata DB backups and PITR.
- Event log stored durably in object storage.
- Rebuild live match state from event log.
- Cross-region read replicas for metadata.
- Active-active API/read serving.
- Active-passive or active-active live processing depending on complexity.

For live scoring, avoid split-brain:

> Only one active processor should own a match event stream at a time.

---

## 20. WebSocket Deep Dive

The WebSocket path is the hot path for users actively watching a live match. Its job is to deliver live score/commentary updates to millions of clients with low latency while handling reconnects, backpressure, and hot-match fanout.

Core principle:

> WebSocket delivery is a real-time delivery layer, not the source of truth. Clients can always recover missed state using sequence numbers and REST APIs.

### 20.1 WebSocket path in the architecture

```text
Live Match Processor
        |
        v
Kafka topic: live-match-updates
        |
        v
Realtime Fanout Service
        |
        v
Regional WebSocket Gateway Fleet
        |
        v
Connected Clients
```

Detailed:

```text
Canonical Match Event
        |
        v
Live Match Processor
        |
        +--------------------+
        |                    |
        v                    v
Redis live_match:{matchId}   Kafka live-match-updates
                                  |
                                  v
                         Realtime Fanout Service
                                  |
                                  v
                         WebSocket Gateways
                                  |
                                  v
                         Mobile/Web Clients
```

### 20.2 Why WebSocket?

Use WebSocket because:

- Bidirectional.
- Low latency.
- Efficient for active live match screens.
- Server can push updates without polling.
- Client can subscribe/unsubscribe to matches dynamically.

| Mechanism | Use case | Trade-off |
|---|---|---|
| WebSocket | Best for live match screens | More connection management |
| SSE | Simple one-way updates | Less flexible, browser-focused |
| Long polling | Fallback | Higher overhead |
| Push notification | Important alerts | Not suitable for ball-by-ball feed |

Recommended:

```text
WebSocket primary
SSE optional for web
REST polling fallback
Push notifications for alerts
```

### 20.3 Client connection flow

```text
Client opens app live match page
  -> Connect WebSocket
  -> Authenticate
  -> Subscribe to matchId
  -> Server sends current snapshot
  -> Server streams updates
```

Example:

```text
Client
  -> WSS /v1/live?token=jwt
  -> SUBSCRIBE_MATCH match_123
  <- SNAPSHOT seq=780
  <- UPDATE seq=781
  <- UPDATE seq=782
```

Endpoint:

```text
WSS /v1/live
```

Auth options:

```text
Authorization: Bearer <JWT>
```

or a short-lived token:

```text
wss://live.example.com/v1/live?token=abc
```

Prefer header/cookie auth where supported. Query tokens can leak in logs if not handled carefully.

Subscribe message:

```json
{
  "type": "SUBSCRIBE_MATCH",
  "matchId": "match_456",
  "lastSeenSeq": 782
}
```

`lastSeenSeq` helps recovery. If the client already has updates up to sequence 782, the server only needs to send newer data.

Server snapshot response:

```json
{
  "type": "MATCH_SNAPSHOT",
  "matchId": "match_456",
  "sequenceNumber": 785,
  "payload": {
    "score": {
      "runs": 149,
      "wickets": 3,
      "overs": "16.5"
    },
    "batsmen": [],
    "bowler": {},
    "lastEvent": {
      "type": "FOUR",
      "text": "Boundary through midwicket"
    }
  }
}
```

Why snapshot first?

- Client gets a consistent current state immediately.
- Client does not need to replay many missed updates.
- Reconnect handling is simpler.

### 20.4 Update message format

Each update must have a monotonic sequence number per match.

```json
{
  "type": "LIVE_SCORE_UPDATE",
  "matchId": "match_456",
  "sequenceNumber": 786,
  "eventType": "BALL_RESULT",
  "deliveryId": "1.16.6",
  "payload": {
    "score": {
      "runs": 153,
      "wickets": 3,
      "overs": "17.0"
    },
    "event": {
      "runs": 4,
      "boundary": true,
      "wicket": false,
      "text": "Driven through covers for four."
    }
  },
  "sentAt": "2026-09-30T07:00:00Z"
}
```

Important fields:

| Field | Why |
|---|---|
| `matchId` | Subscription routing |
| `sequenceNumber` | Ordering/recovery |
| `eventType` | UI rendering |
| `deliveryId` | Ball identity |
| `payload` | Score/commentary delta |
| `sentAt` | Latency measurement |

### 20.5 Subscription management

Each gateway maintains local subscription state:

```text
connectionId -> subscribed matchIds
matchId -> set(connectionIds)
```

Example:

```text
conn_1 -> [match_123, match_456]
match_123 -> [conn_1, conn_2, conn_3]
```

When a Kafka update arrives for `match_123`:

```text
gateway looks up match_123 subscribers
broadcast update to those connections
```

Gateway state is ephemeral and rebuildable through client reconnect/resubscribe.

### 20.6 Fanout models

#### Option A: Every gateway consumes all live updates

```text
Kafka live updates
      |
      v
All WebSocket gateways consume all updates
      |
      v
Each gateway filters by local subscribers
```

Pros:

- Simple.
- No central routing registry.
- Easy failover.

Cons:

- Wasteful if many matches and gateways.
- Every gateway sees updates it may not need.

Good for v1 if update volume is manageable.

#### Option B: Subscription-aware fanout

```text
Gateway registers:
gateway_1 has subscribers for match_123

Fanout Service sends match_123 updates only to gateway_1
```

Pros:

- Less wasted traffic.
- Better for massive scale.

Cons:

- More complex.
- Requires subscription registry.
- More failure modes.

Possible registry:

```text
match_subscribers:{matchId} -> gatewayIds
```

#### Option C: Regional fanout tree

For very hot matches:

```text
Kafka
  -> Global Fanout Service
  -> Regional Fanout Nodes
  -> WebSocket Gateways
  -> Clients
```

Recommended evolution:

```text
v1: all gateways consume/filter
v2: subscription-aware fanout
v3: regional fanout tree for mega matches
```

### 20.7 Hot match scaling

For a World Cup final or India-Pakistan match, there may be millions of subscribers.

Strategies:

1. **Regional WebSocket gateways**
   ```text
   India region
   Middle East region
   Europe region
   US region
   ```
   Benefits:
   - Lower latency.
   - Less cross-region connection pressure.
   - Better fault isolation.

2. **Broadcast compact deltas**
   Do not send full scorecard every ball.
   ```json
   {
     "type": "DELTA",
     "matchId": "match_456",
     "sequenceNumber": 786,
     "patch": {
       "score.runs": 153,
       "score.overs": "17.0",
       "lastEvent": "FOUR"
     }
   }
   ```

3. **Batch micro-updates**
   Commentary, analytics, and score may produce multiple updates for one ball. Batch within:
   ```text
   50-200 ms
   ```

4. **Use local gateway cache**
   Each gateway can keep latest snapshot per hot match:
   ```text
   local_live_match:{matchId}
   ```
   When a user subscribes, gateway sends snapshot from local memory instead of hitting Redis.

### 20.8 Backpressure and slow clients

Some clients may be slow due to network or device issues. Do not allow slow clients to exhaust gateway memory.

Per-connection outbound buffer:

```text
maxBufferMessages = 100
```

Backpressure policy:

```text
if client buffer small:
    send deltas normally

if client buffer growing:
    coalesce updates

if client buffer too large:
    drop deltas and send latest snapshot

if still unhealthy:
    disconnect client
```

For live score:

> Latest state matters more than delivering every intermediate delta.

### 20.9 Sequence numbers and gap recovery

Every match update has:

```text
sequenceNumber
```

Client tracks:

```text
lastSeenSeq
```

If client receives:

```text
expected = 786
received = 788
```

Then the client detects a gap.

Recovery options:

```http
GET /v1/matches/{matchId}/updates?sinceSeq=785
```

or:

```http
GET /v1/matches/{matchId}/live
```

For small gaps, missed updates are fine. For large gaps, send latest snapshot.

### 20.10 Reconnect flow

```text
1. Connection drops.
2. Client reconnects.
3. Client sends SUBSCRIBE_MATCH with lastSeenSeq.
4. Gateway checks if updates after lastSeenSeq are available.
5. If available, send missed updates.
6. Otherwise, send latest snapshot.
7. Resume streaming.
```

Client message:

```json
{
  "type": "SUBSCRIBE_MATCH",
  "matchId": "match_456",
  "lastSeenSeq": 782
}
```

Server response:

```json
{
  "type": "RESUME_OK",
  "fromSeq": 783,
  "toSeq": 786
}
```

or:

```json
{
  "type": "SNAPSHOT_REQUIRED",
  "reason": "CLIENT_TOO_FAR_BEHIND"
}
```

### 20.11 WebSocket gateway state

Gateways should be mostly stateless, except ephemeral connection state.

Ephemeral state:

```text
connectionId
userId/deviceId
subscribedMatchIds
lastAckedSeq per match
outboundBuffer
lastHeartbeatAt
```

Durable state lives in:

- Kafka/event log.
- Redis latest state.
- Commentary store.
- Match event store.

### 20.12 Heartbeats

Server sends:

```json
{
  "type": "PING",
  "timestamp": "..."
}
```

Client responds:

```json
{
  "type": "PONG",
  "timestamp": "..."
}
```

Policy:

```text
ping every 20-30 seconds
disconnect if no pong after 2 missed intervals
```

### 20.13 Load balancing

Use a Layer 4 or WebSocket-aware load balancer.

Important:

- WebSocket connections are long-lived.
- Load balancer must support connection upgrade.
- No need for sticky sessions across reconnect if gateway can recover subscription from client.

Flow:

```text
Client -> LB -> WebSocket Gateway
```

On reconnect, the client may land on a different gateway. That is acceptable because the client resends subscriptions and last seen sequence.

### 20.14 Multi-region WebSocket design

Route users to nearest healthy region:

```text
Geo DNS / Anycast -> nearest WebSocket region
```

Match processing can happen in one primary region, then updates replicate to regional fanout clusters:

```text
Primary match processor
      |
      v
Kafka/global replication
      |
      v
Regional fanout services
      |
      v
Regional WebSocket gateways
```

Avoid multiple processors writing the same match state in active-active mode unless there is strict ownership.

### 20.15 WebSocket failure scenarios

#### Gateway crashes

Impact:

- Connected users disconnect.

Recovery:

```text
Clients reconnect
Clients resubscribe
Clients send lastSeenSeq
Gateway sends missed updates or snapshot
```

No data loss because gateway is not source of truth.

#### Fanout service lag

Impact:

- WebSocket updates delayed.

Handling:

- Show stale indicator if client detects old `sentAt`.
- Scale fanout consumers.
- Clients can poll REST fallback.
- Alert on fanout lag.

#### Redis unavailable

Impact:

- Snapshot on subscribe may fail.

Fallback:

- Use local gateway snapshot cache if available.
- Use live state store or event log reconstruction for lower QPS.
- Continue streaming deltas if Kafka updates still flow.

#### Kafka delay

Impact:

- Updates delayed globally.

Handling:

- API returns latest Redis state.
- UI shows last updated timestamp.
- Alert on consumer lag.
- Prioritize hot live match topics if needed.

### 20.16 API and message types

Client to server:

```text
SUBSCRIBE_MATCH
UNSUBSCRIBE_MATCH
PING/PONG
ACK
SET_PREFERENCES
```

Server to client:

```text
MATCH_SNAPSHOT
LIVE_SCORE_UPDATE
COMMENTARY_UPDATE
ANALYTICS_UPDATE
NOTIFICATION
HEARTBEAT
ERROR
RESYNC_REQUIRED
```

### 20.17 Delivery guarantees

WebSocket delivery is:

```text
best effort + sequence recovery
```

Do not promise exactly-once delivery to clients.

| Guarantee | How |
|---|---|
| Ordered per connection | Send updates by sequence |
| Gap detection | Client tracks sequence numbers |
| Recovery | REST missed-updates or snapshot |
| No source-of-truth loss | Kafka/event log |
| No gateway state dependency | Client resubscribes |

### 20.18 WebSocket security

- Authenticate WebSocket upgrade.
- Use WSS only.
- Validate subscription authorization if premium/private data exists.
- Rate-limit subscriptions per user/device.
- Limit max subscribed matches per connection.
- Prevent malicious clients from sending fake score updates.
- Do not expose internal event payloads.
- Protect query-token logging if token is in URL.

### 20.19 Operational metrics

Gateway metrics:

```text
active_connections
connections_per_region
subscriptions_per_match
messages_sent_per_second
outbound_buffer_size
slow_client_count
disconnect_count
reconnect_rate
heartbeat_timeout_count
```

Fanout metrics:

```text
fanout_lag
kafka_consumer_lag
broadcast_latency
messages_dropped
snapshot_sent_count
resync_required_count
```

Client-experience metrics:

```text
update_end_to_end_latency
gap_detected_count
average_reconnect_time
snapshot_load_latency
```

### 20.20 WebSocket design summary

```text
Client
  -> connects via WSS
  -> authenticates
  -> subscribes to matchId with lastSeenSeq
  <- receives latest snapshot
  <- receives ordered updates with sequenceNumber
  -> detects gaps if sequence missing
  -> recovers via missed-updates API or latest snapshot
```

Server side:

```text
Live Match Processor
  -> writes latest state to Redis
  -> publishes live update to Kafka
  -> Fanout Service consumes update
  -> WebSocket Gateway broadcasts to local subscribers
```

For hot matches:

```text
regional gateways
compact deltas
local snapshot cache
backpressure handling
REST fallback
sequence-based recovery
```

---

## 21. Final Architecture Summary

```text
Official Feed / Scorer / Commentary Editor
        |
        v
Ingestion Gateway
        |
        v
Validation + Normalization
        |
        v
Kafka match-events
        |
        +----------------------+----------------------+----------------+
        |                      |                      |
        v                      v                      v
Live Match Processor     Commentary Processor    Analytics Processor
        |                      |                      |
        v                      v                      v
Redis Live State         Commentary Store         Analytics Store
        |                      |                      |
        +----------+-----------+----------------------+
                   |
                   v
           API Gateway / Feed API
                   |
        +----------+-----------+
        |                      |
        v                      v
REST Clients          WebSocket/SSE Gateway
        |                      |
        v                      v
Mobile/Web Users      Live Subscribers
```

---

## 22. Senior-Level Closing Statement

A strong interview summary:

> I would model the system around an append-only match event stream. Official data providers and scorers emit canonical match events that are validated, ordered by matchId, and published to Kafka. Live score, scorecard, commentary, analytics, notifications, caches, and WebSocket updates are all derived from this event stream. Redis stores hot live match state for low-latency reads, while durable event logs and databases store historical data and enable replay/correction. WebSocket or SSE gateways push real-time updates to active users, with REST polling as fallback. The system scales by partitioning event processing by match, caching hot read paths, horizontally scaling fanout gateways, and using sequence numbers so clients can recover from missed updates. Score correctness is maintained through ordered event processing and audited correction events, while analytics and notifications can be eventually consistent.
