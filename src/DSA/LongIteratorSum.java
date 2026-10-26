package DSA;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

interface LongIterator {
    boolean hasNext();

    long next();
}

public class LongIteratorSum {

    public static long sum(LongIterator iterator) {
        Objects.requireNonNull(iterator, "iterator cannot be null");

        long total = 0L;
        while (iterator.hasNext()) {
            total += iterator.next();
        }
        return total;
    }

    public static long sumWithTwoThreads(LongIterator iterator) throws InterruptedException {
        Objects.requireNonNull(iterator, "iterator cannot be null");

        AtomicLong total = new AtomicLong(0L);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Runnable worker = () -> {
            try {
                while (failure.get() == null) {
                    long value;
                    synchronized (iterator) {
                        if (!iterator.hasNext()) {
                            return;
                        }
                        value = iterator.next();
                    }
                    total.addAndGet(value);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        };

        Thread firstWorker = new Thread(worker, "long-iterator-sum-worker-1");
        Thread secondWorker = new Thread(worker, "long-iterator-sum-worker-2");

        firstWorker.start();
        secondWorker.start();

        firstWorker.join();
        secondWorker.join();

        Throwable throwable = failure.get();
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        if (throwable != null) {
            throw new IllegalStateException("Failed to sum iterator values", throwable);
        }

        return total.get();
    }

    public static void main(String[] args) throws InterruptedException {
        LongIterator iterator = new RangeLongIterator(1, 5);
        System.out.println(sumWithTwoThreads(iterator)); // 15
    }

    private static class RangeLongIterator implements LongIterator {
        private final long endInclusive;
        private long current;

        private RangeLongIterator(long startInclusive, long endInclusive) {
            this.current = startInclusive;
            this.endInclusive = endInclusive;
        }

        @Override
        public boolean hasNext() {
            return current <= endInclusive;
        }

        @Override
        public long next() {
            if (!hasNext()) {
                throw new IllegalStateException("No more elements");
            }
            return current++;
        }
    }
}
