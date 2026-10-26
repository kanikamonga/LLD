# Apache Kafka Fundamentals — Staff Backend Engineer Interview Notes

## 1. One-line Staff-level summary

**Apache Kafka is a distributed commit log for event streaming:** producers append records to partitioned topics, consumers read those records independently, and consumer groups coordinate parallel processing while preserving ordering within each partition.

The key mental model is:

> **Producers write events → Kafka stores them durably in topic partitions → consumers read from partitions → consumer groups divide partitions among consumers for scalable processing.**

---

## 2. Mental model

Kafka is best understood as a **durable, distributed, append-only log**.

Instead of thinking of Kafka as a traditional queue where messages disappear once consumed, think of it as:

> A replicated log where producers append messages and consumers track their own read position.

Core abstractions:

| Concept | Mental model |
|---|---|
| **Topic** | A named stream of related events, such as `orders`, `payments`, or `user-clicks` |
| **Partition** | A shard of a topic; an ordered append-only log |
| **Producer** | Application that writes events into Kafka |
| **Consumer** | Application that reads events from Kafka |
| **Consumer group** | A logical group of consumers that share work |
| **Offset** | Position of a message inside a partition |

Kafka solves problems like:

- Decoupling producers from consumers
- Handling high-throughput event ingestion
- Durable asynchronous processing
- Fan-out to multiple downstream systems
- Replayable event processing
- Parallel processing at scale

Kafka does **not** automatically solve:

- Exactly-once business correctness
- Bad schema evolution
- Poor partition-key choices
- Downstream idempotency
- Infinite scaling without operational trade-offs

---

## 3. Why Kafka matters in backend interviews

Interviewers care about Kafka because it tests whether you understand **distributed asynchronous systems**.

Kafka discussions usually reveal whether a candidate can reason about:

- **Scalability:** Can work be parallelized across partitions and consumers?
- **Reliability:** What happens when consumers crash?
- **Ordering:** Where is ordering guaranteed and where is it not?
- **Backpressure:** What if consumers are slower than producers?
- **Durability:** How are messages retained and replayed?
- **Consistency:** What delivery guarantees are realistic?
- **Operations:** How do you monitor lag, failures, and throughput?

A Staff-level answer should not stop at "Kafka is a message queue." It should explain **why partitioning, offsets, replication, and consumer groups are the foundation of Kafka's design.**

---

## 4. Core concepts

### Producer

A **producer** is an application that sends records to Kafka topics.

Each record usually contains:

- Key
- Value
- Headers
- Timestamp
- Target topic
- Optional partition

Example:

```text
Order Service → produces OrderCreated event → orders topic
```

The producer decides which partition a message goes to, either explicitly or through a partitioning strategy.

Common partitioning strategies:

| Strategy | Behavior |
|---|---|
| Explicit partition | Producer chooses partition directly |
| Key-based partitioning | Same key maps to same partition |
| Round-robin / sticky partitioning | Balances messages when no key is provided |

Why this matters:

- Partition choice affects ordering.
- Partition choice affects load distribution.
- Partition choice affects consumer parallelism.
- Bad keys can create hot partitions.

Example:

```text
Key = customerId
```

All events for the same customer go to the same partition, preserving per-customer order.

### Topic

A **topic** is a named stream of records.

Examples:

```text
orders
payments
shipment-events
user-activity
```

A topic is split into one or more partitions.

You can think of a topic as a logical category, while partitions are the physical units of storage and parallelism.

### Partition

A **partition** is an ordered, append-only log.

Each message in a partition gets a monotonically increasing **offset**.

Example:

```text
orders-topic

Partition 0:
offset 0 → OrderCreated(orderId=1)
offset 1 → OrderPaid(orderId=1)
offset 2 → OrderShipped(orderId=1)

Partition 1:
offset 0 → OrderCreated(orderId=2)
offset 1 → OrderCancelled(orderId=2)
```

Important properties:

- Ordering is guaranteed **only within a partition**.
- Kafka does not guarantee total ordering across all partitions in a topic.
- Partitions enable horizontal scalability.
- More partitions allow more consumer parallelism, up to a point.

This is one of the most important Kafka interview points:

> Kafka ordering is per partition, not per topic.

### Offset

An **offset** is the position of a message within a partition.

Consumers use offsets to track progress.

Example:

```text
Partition 0:
offset 0 consumed
offset 1 consumed
offset 2 next to consume
```

Offsets allow consumers to:

- Resume after failure
- Replay old messages
- Process independently from other consumers
- Control delivery semantics

Kafka stores messages independently of whether they have been consumed. Consumers decide what offset they have processed.

### Consumer

A **consumer** reads records from Kafka partitions.

A consumer pulls data from Kafka, processes it, and commits offsets to record progress.

Example:

```text
Email Service → consumes UserRegistered events → sends welcome email
```

Consumers are responsible for:

- Polling messages
- Processing records
- Committing offsets
- Handling retries
- Ensuring idempotency where needed

Kafka does not push messages to consumers like some traditional messaging systems. Consumers **pull** from Kafka at their own pace.

This helps with backpressure because slow consumers do not block producers directly, but lag can accumulate.

### Consumer group

A **consumer group** is a set of consumers that cooperate to consume a topic.

Within a consumer group:

> Each partition is assigned to at most one consumer at a time.

This gives Kafka queue-like behavior.

Example:

```text
Topic: orders
Partitions: P0, P1, P2

Consumer Group: billing-service
Consumers: C1, C2

Assignment:
C1 → P0, P1
C2 → P2
```

If you add another consumer:

```text
Consumers: C1, C2, C3

Assignment:
C1 → P0
C2 → P1
C3 → P2
```

If you add a fourth consumer but still have only three partitions:

```text
Consumers: C1, C2, C3, C4
Partitions: P0, P1, P2

C4 gets no partition
```

That means:

> Maximum parallelism in a consumer group is bounded by the number of partitions.

---

## 5. How producers, partitions, consumers, and groups interact

The full flow looks like this:

```text
Producer
   |
   | writes event
   v
Kafka Topic
   |
   | partitioned into logs
   v
Partition 0   Partition 1   Partition 2
   |             |             |
   | assigned to consumers within a group
   v             v             v
Consumer A    Consumer B    Consumer C
```

Detailed sequence:

1. **Producer creates a message**
   ```text
   OrderCreated(orderId=123, userId=42)
   ```

2. **Producer sends it to a topic**
   ```text
   topic = orders
   key = userId
   ```

3. **Kafka chooses a partition**
   If the key is `userId`, all events with the same `userId` usually go to the same partition.

4. **Kafka appends the message to that partition**
   The message gets an offset.

5. **Consumer group subscribes to the topic**
   Kafka assigns partitions across consumers in the group.

6. **Each consumer reads from assigned partitions**
   A consumer processes records in offset order per partition.

7. **Consumer commits offsets**
   After processing, the consumer records how far it has progressed.

8. **If a consumer fails**
   Kafka reassigns its partitions to another consumer in the same group.

---

## 6. Distributed message delivery behavior

Kafka can support different communication patterns depending on consumer groups.

### Queue-like processing

If multiple consumers are in the **same consumer group**, they share the work.

```text
Topic: orders

Consumer Group: payment-service
C1 reads P0
C2 reads P1
C3 reads P2
```

Each message is processed by one consumer in that group.

This is useful for:

- Scaling workers
- Background jobs
- Order processing
- Payment processing
- Notification dispatch

### Publish-subscribe fan-out

If consumers are in **different consumer groups**, each group receives its own copy of the stream.

```text
Topic: orders

Consumer Group: payment-service
Consumer Group: inventory-service
Consumer Group: analytics-service
```

Each group independently reads the same events.

This is useful for:

- Multiple downstream services reacting to the same event
- Analytics pipelines
- Audit logging
- Search indexing
- Notifications

The same Kafka topic can behave like both a queue and pub-sub system depending on consumer group design.

---

## 7. Example: order processing system

Suppose an e-commerce system has this event:

```text
OrderCreated(orderId=1001, userId=501, amount=99.99)
```

The flow:

```text
Order Service
   |
   | produces OrderCreated
   v
Kafka topic: orders
   |
   | partitions by orderId or userId
   v
P0 / P1 / P2
   |
   +--> payment-service consumer group
   |
   +--> inventory-service consumer group
   |
   +--> analytics-service consumer group
```

Each service consumes independently:

| Consumer group | Purpose |
|---|---|
| `payment-service` | Charge payment |
| `inventory-service` | Reserve stock |
| `analytics-service` | Update dashboards |
| `email-service` | Send confirmation email |

If `analytics-service` is slow, it does not block `payment-service`.

It may accumulate lag, but each group tracks its own offsets.

That decoupling is one of Kafka's major strengths.

---

## 8. Partitions and ordering

Partitions are the unit of ordering.

If you need all events for the same entity to be processed in order, use a stable key.

Example:

```text
key = orderId
```

Then all events for the same order go to the same partition:

```text
OrderCreated(orderId=123)
OrderPaid(orderId=123)
OrderShipped(orderId=123)
```

This preserves order for that order.

But across different orders, ordering is not guaranteed:

```text
Partition 0: orderId=123 events
Partition 1: orderId=456 events
```

That is usually acceptable because most business workflows need ordering per entity, not globally.

Staff-level framing:

> I would choose the partition key based on the ordering boundary and load distribution. If I need per-order ordering, I use `orderId`; if I need per-customer ordering, I use `customerId`. But I also need to check cardinality and skew to avoid hot partitions.

---

## 9. Consumer groups and parallelism

Consumer groups provide horizontal scale.

Suppose a topic has six partitions:

```text
orders: P0, P1, P2, P3, P4, P5
```

With two consumers:

```text
C1 → P0, P1, P2
C2 → P3, P4, P5
```

With three consumers:

```text
C1 → P0, P1
C2 → P2, P3
C3 → P4, P5
```

With six consumers:

```text
C1 → P0
C2 → P1
C3 → P2
C4 → P3
C5 → P4
C6 → P5
```

With eight consumers:

```text
Six consumers get partitions.
Two consumers remain idle.
```

Key interview point:

> Adding consumers beyond the partition count does not increase processing parallelism within one consumer group.

To increase parallelism, you usually need more partitions, but that has trade-offs.

---

## 10. Delivery semantics

Kafka delivery semantics depend on producer settings, consumer offset commits, retries, and idempotency.

Common models:

| Model | Meaning | Risk |
|---|---|---|
| At most once | Message may be lost but not retried | Data loss |
| At least once | Message will be processed but may be duplicated | Duplicate side effects |
| Effectively once | System is designed so duplicates do not affect correctness | Requires idempotency/transactions |

Most production systems target:

> **At-least-once delivery + idempotent processing.**

Example:

If a consumer processes a payment event but crashes before committing the offset, Kafka may deliver the event again.

So the payment service must handle duplicates safely, for example using:

- Idempotency keys
- Deduplication table
- Unique transaction IDs
- State machine checks

Staff-level answer:

> Kafka can redeliver messages during failures, so consumers must be idempotent. I would not rely only on Kafka delivery guarantees for business correctness.

---

## 11. Trade-offs

| Decision | Benefit | Cost / Risk | When to choose |
|---|---|---|---|
| Use key-based partitioning | Preserves ordering for same key | Can create hot partitions if key distribution is skewed | When ordering per entity matters |
| Use more partitions | More parallelism and throughput | More metadata, rebalancing cost, operational overhead | When consumer throughput is partition-limited |
| Use fewer partitions | Simpler operations, less overhead | Limited parallelism | Small workloads or strict ordering needs |
| One consumer group per service | Independent offsets and scaling | More total read load on Kafka | Multiple services need same event stream |
| Multiple consumers in same group | Parallel processing | Ordering only per partition; idle consumers if too many | Worker-style processing |
| Commit offset after processing | Reduces message loss | Can cause duplicate processing after crash | Most reliable processing flows |
| Commit offset before processing | Lower duplicate risk | Can lose messages on crash | Rare cases where loss is acceptable |
| Long retention | Enables replay and recovery | More storage cost | Audit, reprocessing, ML, analytics |
| Short retention | Lower storage cost | Less replay window | Ephemeral event streams |

---

## 12. Failure modes and mitigations

| Failure mode | Symptom | Root cause | Mitigation | Interview signal |
|---|---|---|---|---|
| Consumer lag grows | Consumers fall behind | Processing slower than production rate | Scale consumers, optimize processing, increase partitions, add batching | Mention lag as a first-class operational metric |
| Duplicate processing | Same event causes repeated side effect | Consumer crashes before offset commit | Idempotent consumers, deduplication, transactional writes | Strong candidates separate delivery from business correctness |
| Hot partition | One partition overloaded | Skewed partition key | Better key design, salting, repartitioning | Mention key cardinality and distribution |
| Rebalance storms | Consumers repeatedly lose/reacquire partitions | Unstable consumers, long processing, bad configs | Tune heartbeats, max poll interval, cooperative rebalancing | Shows operational maturity |
| Out-of-order processing | Events processed unexpectedly | Wrong partition key or multiple partitions for same entity | Partition by ordering boundary | State ordering is only per partition |
| Poison message | Consumer repeatedly fails on same record | Bad data or unhandled edge case | DLQ, retry topic, validation, alerting | Avoid infinite retry loops |
| Data loss | Expected message missing | Incorrect producer acks or early offset commit | Use proper acks, replication, commit after processing | Discuss end-to-end reliability |
| Slow downstream dependency | Kafka lag increases | Consumer blocked on DB/API | Timeouts, circuit breakers, async processing, backpressure | Connect Kafka health to downstream health |

---

## 13. Observability and operations

Important metrics:

| Area | Metrics |
|---|---|
| Producer | produce rate, error rate, retry rate, request latency, batch size |
| Broker/topic | throughput, partition count, replication health, under-replicated partitions |
| Consumer | consumer lag, processing latency, commit latency, rebalance count |
| Partition | per-partition throughput, hot partitions, log size |
| End-to-end | event age, time from produce to successful processing |

Key alerts:

- Consumer lag exceeds threshold
- Lag increasing continuously
- Under-replicated partitions
- Producer error rate spike
- Consumer rebalance frequency spike
- Dead-letter topic growth
- Processing latency SLO breach

Operational Staff-level point:

> I would monitor not only Kafka infrastructure metrics but also business-level event age, because low broker error rates do not guarantee that the system is processing events within the required SLA.

---

## 14. Staff-level talking points

Strong interview framing:

> Kafka is useful when I need durable, scalable, asynchronous event streaming. Producers append events to topic partitions, Kafka stores them durably, and consumers read at their own pace. Consumer groups allow services to scale horizontally by assigning partitions across instances, while offsets let each group track progress independently.

On partitioning:

> The partition key is a design decision. It defines both the ordering boundary and the load distribution. I would choose it based on the entity that requires ordered processing, while checking for cardinality and skew.

On consumer groups:

> Consumers in the same group divide work, so a message is processed by one consumer in that group. Consumers in different groups each receive the stream independently, enabling pub-sub fan-out.

On reliability:

> Kafka gives durable storage and offset-based replay, but application correctness still depends on idempotent consumers, safe offset commits, retry strategy, and downstream consistency.

On scaling:

> Scaling consumers only helps until the number of active consumers reaches the number of partitions. If we need more parallelism, we may need more partitions, but that increases operational complexity and can affect ordering guarantees.

---

## 15. Common interview questions

### Q1. What is the difference between a topic and a partition?

**What interviewer is testing:** Whether you understand Kafka's storage and scalability model.

**Strong answer outline:**

- Topic is a logical event stream.
- Partition is a physical shard of that stream.
- Each partition is ordered.
- A topic can have multiple partitions for scale.
- Ordering is guaranteed within a partition, not across the topic.

### Q2. Why do consumer groups exist?

**What interviewer is testing:** Whether you understand Kafka's processing model.

**Strong answer outline:**

- A consumer group lets multiple consumers share the work.
- Each partition is assigned to one consumer within a group.
- Different groups can consume the same topic independently.
- This supports both queue-like and pub-sub behavior.

### Q3. What happens if there are more consumers than partitions?

**Strong answer:**

Some consumers will be idle because a partition can be assigned to only one consumer within a consumer group at a time.

### Q4. How does Kafka preserve ordering?

**Strong answer:**

Kafka preserves ordering within a partition. To preserve ordering for a business entity, all events for that entity should use the same partition key.

### Q5. How do consumers resume after failure?

**Strong answer:**

Consumers commit offsets. After failure, another consumer in the same group can resume from the last committed offset for that partition.

### Q6. Can Kafka deliver duplicate messages?

**Strong answer:**

Yes. In common at-least-once setups, duplicates can happen if a consumer processes a message but crashes before committing the offset. Consumers should be idempotent.

---

## 16. Mistakes to avoid

Avoid saying:

> "Kafka is just a queue."

Better:

> "Kafka can behave like a queue within a consumer group, but fundamentally it is a distributed log that supports replay and multiple independent consumer groups."

Avoid saying:

> "Kafka guarantees ordering."

Better:

> "Kafka guarantees ordering only within a partition."

Avoid saying:

> "Add more consumers to scale Kafka."

Better:

> "Add more consumers up to the number of partitions in the consumer group. Beyond that, consumers will be idle unless partition count increases."

Avoid saying:

> "Kafka guarantees exactly-once processing."

Better:

> "Kafka has exactly-once features in specific producer/stream-processing scenarios, but end-to-end business correctness usually still requires idempotent processing and careful transactional boundaries."

---

## 17. Quick revision cheat sheet

- Kafka is a **distributed append-only log**, not just a queue.
- Producers write records to topics.
- Topics are split into partitions.
- Partitions provide ordering and parallelism.
- Ordering is guaranteed only **within a partition**.
- A partition key controls where messages go.
- Same key usually means same partition.
- Consumers read records and track offsets.
- Offsets represent consumer progress.
- Consumer groups divide partitions among consumers.
- Same consumer group = load sharing.
- Different consumer groups = pub-sub fan-out.
- Maximum active consumers in a group is limited by partition count.
- Consumer lag measures how far consumers are behind.
- Kafka can redeliver messages; consumers should be idempotent.
- Strong Kafka design requires good partition keys, offset strategy, retry handling, DLQs, observability, and operational ownership.
