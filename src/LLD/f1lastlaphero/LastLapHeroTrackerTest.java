package LLD.f1lastlaphero;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain-main test harness (consistent with this repo's existing convention,
 * e.g. SingletonTest - no JUnit/build tool wired up).
 */
public final class LastLapHeroTrackerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        run("workedExampleMatchesExpectedHero", LastLapHeroTrackerTest::workedExampleMatchesExpectedHero);
        run("heroUpdatesAsNewLapsComeIn", LastLapHeroTrackerTest::heroUpdatesAsNewLapsComeIn);
        run("driversAtDifferentLapCountsAreComparedFairly", LastLapHeroTrackerTest::driversAtDifferentLapCountsAreComparedFairly);
        run("queryingBeforeAnyLapsThrows", LastLapHeroTrackerTest::queryingBeforeAnyLapsThrows);
        run("tieBrokenByLexicographicallySmallestDriverId", LastLapHeroTrackerTest::tieBrokenByLexicographicallySmallestDriverId);
        run("concurrentUpdatesKeepIndexConsistent", LastLapHeroTrackerTest::concurrentUpdatesKeepIndexConsistent);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
            System.out.println("PASS - " + name);
            passed++;
        } catch (Throwable t) {
            System.out.println("FAIL - " + name + " : " + t);
            failed++;
        }
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + " - expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }

    static void workedExampleMatchesExpectedHero() {
        LastLapHeroTracker tracker = new LastLapHeroTracker();
        tracker.updateLap("Driver1", 100);
        tracker.updateLap("Driver2", 90);
        tracker.updateLap("Driver1", 110);
        tracker.updateLap("Driver2", 95);

        assertEquals("Driver1", tracker.getLastLapHero(), "Driver1 should have the higher gain (5 vs 2.5)");
    }

    static void heroUpdatesAsNewLapsComeIn() {
        LastLapHeroTracker tracker = new LastLapHeroTracker();
        tracker.updateLap("A", 100);
        tracker.updateLap("B", 50);
        // A: avg=100, gain=0. B: avg=50, gain=0. Tie -> "A" (lexicographically smaller).
        assertEquals("A", tracker.getLastLapHero(), "first laps always have gain 0; tie broken by id");

        // B has a bad (slow) lap relative to its average -> higher gain, should overtake.
        tracker.updateLap("B", 200); // B avg=(50+200)/2=125, gain=200-125=75
        assertEquals("B", tracker.getLastLapHero(), "B should become hero after its slow last lap");

        // A now also has a much slower last lap than its average.
        tracker.updateLap("A", 300); // A avg=(100+300)/2=200, gain=300-200=100
        assertEquals("A", tracker.getLastLapHero(), "A should retake hero with a larger gain");
    }

    static void driversAtDifferentLapCountsAreComparedFairly() {
        LastLapHeroTracker tracker = new LastLapHeroTracker();
        // Driver A on lap 5, Driver B on lap 10 - gain formula only depends on
        // each driver's OWN average, so differing lap counts are handled naturally.
        for (int i = 0; i < 4; i++) {
            tracker.updateLap("A", 90); // steady laps
        }
        tracker.updateLap("A", 150); // A: avg=(4*90+150)/5=102, gain=150-102=48

        for (int i = 0; i < 9; i++) {
            tracker.updateLap("B", 80); // steady laps
        }
        tracker.updateLap("B", 100); // B: avg=(9*80+100)/10=82, gain=100-82=18

        assertEquals("A", tracker.getLastLapHero(), "A has the larger gain despite fewer laps");
    }

    static void queryingBeforeAnyLapsThrows() {
        LastLapHeroTracker tracker = new LastLapHeroTracker();
        try {
            tracker.getLastLapHero();
            throw new AssertionError("expected NoLapDataException");
        } catch (NoLapDataException expected) {
            // good
        }
    }

    static void tieBrokenByLexicographicallySmallestDriverId() {
        LastLapHeroTracker tracker = new LastLapHeroTracker();
        tracker.updateLap("Zed", 100);
        tracker.updateLap("Alpha", 100);
        // Both have gain=0 on their first lap -> tie broken by id.
        assertEquals("Alpha", tracker.getLastLapHero(), "tie should break to lexicographically smallest id");
    }

    /**
     * Many threads hammer updateLap()/getLastLapHero() concurrently for a
     * fixed, disjoint set of driver ids. We can't assert a specific hero
     * (updates interleave nondeterministically), but we CAN assert the
     * tracker never throws/corrupts and always returns a value consistent
     * with driverStats bookkeeping - i.e. the reported hero's gain equals
     * the max gain found by scanning all drivers' snapshots afterward.
     */
    static void concurrentUpdatesKeepIndexConsistent() {
        LastLapHeroTracker tracker = new LastLapHeroTracker();
        int driverCount = 20;
        int lapsPerDriver = 200;
        String[] driverIds = new String[driverCount];
        for (int i = 0; i < driverCount; i++) driverIds[i] = "D" + i;

        ExecutorService pool = Executors.newFixedThreadPool(driverCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(driverCount);
        AtomicInteger errors = new AtomicInteger();
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();

        for (String driverId : driverIds) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
                    for (int lap = 0; lap < lapsPerDriver; lap++) {
                        tracker.updateLap(driverId, r.nextInt(1, 1000));
                        tracker.getLastLapHero(); // exercise concurrent reads too
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished;
        try {
            finished = doneLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pool.shutdownNow();
        }

        assertTrue(finished, "all threads should finish within timeout");
        assertEquals(0, errors.get(), "no exceptions should occur during concurrent access");

        // Cross-check: reported hero's gain must equal the true max across all drivers.
        String reportedHero = tracker.getLastLapHero();
        double reportedGain = tracker.getDriverStats(reportedHero).gain();

        double trueMaxGain = Double.NEGATIVE_INFINITY;
        for (String driverId : driverIds) {
            double g = tracker.getDriverStats(driverId).gain();
            trueMaxGain = Math.max(trueMaxGain, g);
        }
        assertEquals(trueMaxGain, reportedGain, "reported hero's gain should equal the true max gain");
    }
}
