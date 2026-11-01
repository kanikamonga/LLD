package LLD.underground;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Dependency-free tests for averages, lifecycle, key collisions, and concurrency. */
public final class UndergroundSystemTest {
    public static void main(String[] args) throws Exception {
        UndergroundSystem system = new UndergroundSystem();
        system.checkIn(1, "A", 10);
        system.checkIn(2, "A", 20);
        system.checkOut(1, "B", 21);
        equal(11, system.getAverageTime("A", "B"));
        system.checkOut(2, "B", 32);
        equal(11.5, system.getAverageTime("A", "B"));
        system.checkIn(1, "B", 40);
        system.checkOut(1, "A", 45);
        equal(5, system.getAverageTime("B", "A"));
        equal(11.5, system.getAverageTime("A", "B"));

        system.checkIn(1, "A|B", 50);
        system.checkOut(1, "C", 60);
        system.checkIn(1, "A", 70);
        system.checkOut(1, "B|C", 100);
        equal(10, system.getAverageTime("A|B", "C"));
        equal(30, system.getAverageTime("A", "B|C"));

        system.checkIn(1, "Same", Integer.MIN_VALUE);
        system.checkOut(1, "Same", Integer.MAX_VALUE);
        equal(4_294_967_295L, system.getAverageTime("Same", "Same"));

        expect(IllegalStateException.class, () -> system.checkOut(999, "B", 20));
        expect(IllegalStateException.class, () -> system.getAverageTime("Unknown", "B"));
        expect(IllegalArgumentException.class, () -> system.checkIn(1, " ", 1));
        system.checkIn(1, "A", 100);
        expect(IllegalStateException.class, () -> system.checkIn(1, "B", 101));
        expect(IllegalArgumentException.class, () -> system.checkOut(1, "B", 100));
        system.checkOut(1, "B", 120); // Failed operations did not discard the active trip.
        equal(43.0 / 3, system.getAverageTime("A", "B"));
        concurrentTrips();
        concurrentDuplicateCheckIn();
        System.out.println("All underground system tests passed");
    }

    private static void concurrentTrips() throws Exception {
        UndergroundSystem system = new UndergroundSystem();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int id = i;
                tasks.add(pool.submit(() -> {
                    start.await();
                    system.checkIn(id, "A", 0);
                    system.checkOut(id, "B", id + 1);
                    double average = system.getAverageTime("A", "B");
                    if (average < 1 || average > 100) {
                        throw new AssertionError("Invalid concurrent average: " + average);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            equal(50.5, system.getAverageTime("A", "B"));
        } finally {
            shutdown(pool);
        }
    }

    private static void concurrentDuplicateCheckIn() throws Exception {
        UndergroundSystem system = new UndergroundSystem();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                tasks.add(pool.submit(() -> {
                    start.await();
                    try {
                        system.checkIn(1, "A", 10);
                        return true;
                    } catch (IllegalStateException expected) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> task : tasks) {
                if (task.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            equal(1, accepted);
        } finally {
            shutdown(pool);
        }
    }

    private static void shutdown(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Worker shutdown timed out");
        }
    }

    private static void equal(double expected, double actual) {
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError("Expected " + expected + ", received " + actual);
        }
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            if (!type.isInstance(e)) {
                throw e;
            }
            return;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
