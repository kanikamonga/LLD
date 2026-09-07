package LLD.multithreading;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory Producer-Consumer demo showing strategies when producers outrun consumers.
 * Run main() to see metrics for different overflow strategies.
 */
public class ProducerConsumerDemo {

    static final class Message {
        final int id;
        final Instant ts = Instant.now();
        Message(int id) { this.id = id; }
        @Override public String toString() { return "M{" + id + '}'; }
    }

    enum OverflowStrategy {
        BLOCK_PRODUCER,    // producer blocks until space available (backpressure)
        DROP_NEW,          // drop the newly produced message
        DROP_OLD,          // evict oldest message to make room for new
        OFFER_WITH_TIMEOUT // try to offer with timeout, drop if timeout
    }

    static final class MessageQueue {
        private final BlockingQueue<Message> q;
        private final OverflowStrategy strategy;
        private final long offerTimeoutMs;

        // metrics
        final AtomicInteger dropped = new AtomicInteger();
        final AtomicInteger produced = new AtomicInteger();
        final AtomicInteger consumed = new AtomicInteger();

        MessageQueue(int capacity, OverflowStrategy strategy, long offerTimeoutMs) {
            this.q = new ArrayBlockingQueue<>(capacity);
            this.strategy = strategy;
            this.offerTimeoutMs = offerTimeoutMs;
        }

        void publish(Message m) throws InterruptedException {
            produced.incrementAndGet();
            switch (strategy) {
                case BLOCK_PRODUCER:
                    q.put(m); // blocks until space
                    return;
                case DROP_NEW:
                    boolean ok = q.offer(m);
                    if (!ok) dropped.incrementAndGet();
                    return;
                case DROP_OLD:
                    // If full, remove oldest and insert new
                    if (!q.offer(m)) {
                        Message old = q.poll(); // may be null if concurrent changed
                        if (old != null) {
                            dropped.incrementAndGet(); // counting the evicted message as dropped
                        }
                        // try again; if still fails, count new as dropped
                        if (!q.offer(m)) {
                            dropped.incrementAndGet();
                        }
                    }
                    return;
                case OFFER_WITH_TIMEOUT:
                    boolean offered = q.offer(m, offerTimeoutMs, TimeUnit.MILLISECONDS);
                    if (!offered) dropped.incrementAndGet();
                    return;
                default:
                    throw new IllegalStateException("unknown strategy");
            }
        }

        Message consume(long timeoutMs) throws InterruptedException {
            Message m = q.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (m != null) consumed.incrementAndGet();
            return m;
        }

        int backlog() { return q.size(); }
    }

    static final class Producer implements Callable<Void> {
        private final MessageQueue mq;
        private final int idStart;
        private final int produceCount;
        private final long intervalMs; // time between produces

        Producer(MessageQueue mq, int idStart, int produceCount, long intervalMs) {
            this.mq = mq; this.idStart = idStart; this.produceCount = produceCount; this.intervalMs = intervalMs;
        }

        @Override public Void call() {
            try {
                for (int i = 0; i < produceCount; i++) {
                    Message m = new Message(idStart + i);
                    mq.publish(m);
                    // simulate produce rate
                    Thread.sleep(intervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    static final class Consumer implements Callable<Void> {
        private final MessageQueue mq;
        private final long processMs; // time to process a message
        private final int id;
        private final int runMillis;

        Consumer(MessageQueue mq, int id, long processMs, int runMillis) {
            this.mq = mq; this.processMs = processMs; this.id = id; this.runMillis = runMillis;
        }

        @Override public Void call() {
            long end = System.currentTimeMillis() + runMillis;
            try {
                while (System.currentTimeMillis() < end) {
                    Message m = mq.consume(500);
                    if (m != null) {
                        // simulate processing
                        Thread.sleep(processMs);
                        // processed
                    } else {
                        // no message, idle briefly
                        Thread.sleep(10);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    // Demo runner
    public static void main(String[] args) throws Exception {
        // Scenario parameters
        final int queueCapacity = 50;
        final int producerThreads = 4;
        final int consumerThreads = 2; // consumers cannot scale enough
        final int messagesPerProducer = 1000;
        final long consumerProcessMs = 10; // time to process each message

        // produce interval: when doubled-rate scenario, make producers faster
        final long normalProduceMs = 5; // base
        final long doubledProduceMs = normalProduceMs / 2; // faster => more messages

        List<OverflowStrategy> strategies = List.of(
                OverflowStrategy.BLOCK_PRODUCER,
                OverflowStrategy.DROP_NEW,
                OverflowStrategy.DROP_OLD,
                OverflowStrategy.OFFER_WITH_TIMEOUT
        );

        for (OverflowStrategy strat : strategies) {
            System.out.println("\n=== Strategy: " + strat + " ===");
            runScenario(queueCapacity, strat, producerThreads, consumerThreads,
                    messagesPerProducer, doubledProduceMs, consumerProcessMs);
        }
    }

    static void runScenario(int queueCapacity, OverflowStrategy strat,
                            int producerThreads, int consumerThreads,
                            int messagesPerProducer, long produceIntervalMs, long consumerProcessMs) throws Exception {

        MessageQueue mq = new MessageQueue(queueCapacity, strat, 50);

        ExecutorService prodExec = Executors.newFixedThreadPool(producerThreads);
        ExecutorService consExec = Executors.newFixedThreadPool(consumerThreads);

        List<Callable<Void>> producers = new ArrayList<>();
        for (int p = 0; p < producerThreads; p++) {
            producers.add(new Producer(mq, p * messagesPerProducer, messagesPerProducer, produceIntervalMs));
        }

        List<Callable<Void>> consumers = new ArrayList<>();
        int runMillis = 2000; // run consumers for this many ms
        for (int c = 0; c < consumerThreads; c++) {
            consumers.add(new Consumer(mq, c, consumerProcessMs, runMillis));
        }

        long start = System.currentTimeMillis();

        // start consumers
        List<Future<Void>> cf = new ArrayList<>();
        for (Callable<Void> c : consumers) cf.add(consExec.submit(c));

        // start producers
        List<Future<Void>> pf = new ArrayList<>();
        for (Callable<Void> p : producers) pf.add(prodExec.submit(p));

        // wait producers finish
        for (Future<Void> f : pf) f.get();
        // wait consumers to finish
        for (Future<Void> f : cf) f.get();

        long elapsed = System.currentTimeMillis() - start;

        // drain remaining queue items (not processed)
        int remaining = mq.backlog();

        System.out.printf("elapsed=%dms produced=%d consumed=%d dropped=%d backlog=%d\n",
                elapsed, mq.produced.get(), mq.consumed.get(), mq.dropped.get(), remaining);

        prodExec.shutdownNow();
        consExec.shutdownNow();
    }
}
