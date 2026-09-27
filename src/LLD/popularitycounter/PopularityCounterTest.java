package LLD.popularitycounter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain-main test harness, consistent with this repo's existing convention
 * (see SingletonTest / FoodOrderingSystemTest) - no JUnit or build tool is
 * wired up in this project.
 */
public final class PopularityCounterTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        run("addUserStartsAtZeroVotes", PopularityCounterTest::addUserStartsAtZeroVotes);
        run("incrementAndDecrementTrackVotes", PopularityCounterTest::incrementAndDecrementTrackVotes);
        run("topUserFollowsTheLeadAsVotesChange", PopularityCounterTest::topUserFollowsTheLeadAsVotesChange);
        run("votesMayGoNegative", PopularityCounterTest::votesMayGoNegative);
        run("removeUserPromotesNextBest", PopularityCounterTest::removeUserPromotesNextBest);
        run("tieBrokenByWhoReachedTheCountFirst", PopularityCounterTest::tieBrokenByWhoReachedTheCountFirst);
        run("duplicateAddThrows", PopularityCounterTest::duplicateAddThrows);
        run("unknownUserOperationsThrow", PopularityCounterTest::unknownUserOperationsThrow);
        run("queryingTopUserWithNoUsersThrows", PopularityCounterTest::queryingTopUserWithNoUsersThrows);
        run("emptyThenRepopulatedCounterStillWorks", PopularityCounterTest::emptyThenRepopulatedCounterStillWorks);
        run("concurrentIncrementsAreNotLost", PopularityCounterTest::concurrentIncrementsAreNotLost);
        run("concurrentIncrementDecrementNetsToZero", PopularityCounterTest::concurrentIncrementDecrementNetsToZero);

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

    // ------------------------------------------------------------------

    static void addUserStartsAtZeroVotes() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("a");
        assertEquals(0, c.getVotes("a"), "new user should start at 0 votes");
        assertEquals("a", c.getUserWithMostVote(), "only user should be the top user");
    }

    static void incrementAndDecrementTrackVotes() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("a");
        c.incrementVote("a");
        c.incrementVote("a");
        c.incrementVote("a");
        assertEquals(3, c.getVotes("a"), "three increments should give 3 votes");
        c.decrementVote("a");
        assertEquals(2, c.getVotes("a"), "one decrement should give 2 votes");
    }

    static void topUserFollowsTheLeadAsVotesChange() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("a");
        c.addUser("b");

        c.incrementVote("a");
        assertEquals("a", c.getUserWithMostVote(), "a leads with 1 vote");

        c.incrementVote("b");
        c.incrementVote("b");
        assertEquals("b", c.getUserWithMostVote(), "b should overtake with 2 votes");

        c.incrementVote("a");
        c.incrementVote("a");
        assertEquals("a", c.getUserWithMostVote(), "a should retake the lead with 3 votes");

        // Knocking the leader back down must promote the runner-up.
        c.decrementVote("a");
        c.decrementVote("a");
        assertEquals("b", c.getUserWithMostVote(), "b leads again once a drops to 1");
    }

    static void votesMayGoNegative() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("a");
        c.addUser("b");
        c.decrementVote("a");
        c.decrementVote("a");
        assertEquals(-2, c.getVotes("a"), "votes should be allowed to go negative");
        assertEquals("b", c.getUserWithMostVote(), "b at 0 beats a at -2");

        // And can climb back up through zero.
        c.incrementVote("a");
        c.incrementVote("a");
        c.incrementVote("a");
        assertEquals(1, c.getVotes("a"), "a should climb back through zero to +1");
        assertEquals("a", c.getUserWithMostVote(), "a should lead again at +1");
    }

    static void removeUserPromotesNextBest() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("a");
        c.addUser("b");
        c.incrementVote("a");
        c.incrementVote("a");
        c.incrementVote("b");
        assertEquals("a", c.getUserWithMostVote(), "a leads before removal");

        c.removeUser("a");
        assertEquals("b", c.getUserWithMostVote(), "b should be promoted after a is removed");
        assertEquals(1, c.userCount(), "one user should remain");
    }

    static void tieBrokenByWhoReachedTheCountFirst() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("zed");
        c.addUser("amy");
        c.incrementVote("zed"); // zed reaches 1 first
        c.incrementVote("amy");
        assertEquals("zed", c.getUserWithMostVote(), "tie at 1 vote should go to whoever got there first");
    }

    static void duplicateAddThrows() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("a");
        try {
            c.addUser("a");
            throw new AssertionError("expected DuplicateUserException");
        } catch (DuplicateUserException expected) {
            // good
        }
    }

    static void unknownUserOperationsThrow() {
        PopularityCounter c = new PopularityCounter();
        int thrown = 0;
        try { c.incrementVote("ghost"); } catch (UserNotFoundException e) { thrown++; }
        try { c.decrementVote("ghost"); } catch (UserNotFoundException e) { thrown++; }
        try { c.removeUser("ghost"); }    catch (UserNotFoundException e) { thrown++; }
        try { c.getVotes("ghost"); }      catch (UserNotFoundException e) { thrown++; }
        assertEquals(4, thrown, "all operations on an unknown user should throw UserNotFoundException");
    }

    static void queryingTopUserWithNoUsersThrows() {
        PopularityCounter c = new PopularityCounter();
        try {
            c.getUserWithMostVote();
            throw new AssertionError("expected NoUsersException");
        } catch (NoUsersException expected) {
            // good
        }
    }

    /**
     * Guards the pinned-zero-bucket invariant: draining the counter completely
     * and then re-adding must behave exactly like a fresh counter.
     */
    static void emptyThenRepopulatedCounterStillWorks() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("a");
        c.incrementVote("a");
        c.removeUser("a");
        assertEquals(0, c.userCount(), "counter should be empty");

        c.addUser("b");
        assertEquals(0, c.getVotes("b"), "re-added user should start at 0 again");
        assertEquals("b", c.getUserWithMostVote(), "re-populated counter should report the new user");
    }

    /**
     * Each thread owns a distinct user and increments it a distinct number of
     * times, so the expected final counts AND the expected winner are fully
     * deterministic despite the interleaving. Any lost update would show up as
     * a wrong count or a wrong winner.
     */
    static void concurrentIncrementsAreNotLost() {
        PopularityCounter c = new PopularityCounter();
        int users = 16;
        for (int i = 0; i < users; i++) {
            c.addUser("u" + i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < users; i++) {
            final String userId = "u" + i;
            final int increments = (i + 1) * 50; // u15 gets the most -> deterministic winner
            pool.submit(() -> {
                try {
                    startGate.await();
                    for (int n = 0; n < increments; n++) {
                        c.incrementVote(userId);
                        c.getUserWithMostVote(); // exercise concurrent lock-free reads too
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        startGate.countDown();
        awaitAndShutdown(done, pool);

        assertEquals(0, errors.get(), "no exceptions should occur under concurrent access");
        for (int i = 0; i < users; i++) {
            assertEquals((i + 1) * 50, c.getVotes("u" + i), "no increments should be lost for u" + i);
        }
        assertEquals("u" + (users - 1), c.getUserWithMostVote(), "the user with the most increments should win");
    }

    /**
     * Many threads increment and decrement the SAME user in equal measure.
     * Correct mutual exclusion means the net result is exactly zero; a race in
     * the bucket relinking would corrupt the count (or the list).
     */
    static void concurrentIncrementDecrementNetsToZero() {
        PopularityCounter c = new PopularityCounter();
        c.addUser("contended");

        int threadPairs = 8;
        int opsPerThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threadPairs * 2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadPairs * 2);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < threadPairs; i++) {
            pool.submit(incrementTask(c, startGate, done, errors, opsPerThread, true));
            pool.submit(incrementTask(c, startGate, done, errors, opsPerThread, false));
        }

        startGate.countDown();
        awaitAndShutdown(done, pool);

        assertEquals(0, errors.get(), "no exceptions should occur under contention");
        assertEquals(0, c.getVotes("contended"), "equal increments and decrements must net to zero");
        assertEquals("contended", c.getUserWithMostVote(), "the only user should still be reported as top");
    }

    private static Runnable incrementTask(PopularityCounter c, CountDownLatch startGate, CountDownLatch done,
                                          AtomicInteger errors, int ops, boolean increment) {
        return () -> {
            try {
                startGate.await();
                for (int n = 0; n < ops; n++) {
                    if (increment) {
                        c.incrementVote("contended");
                    } else {
                        c.decrementVote("contended");
                    }
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            } finally {
                done.countDown();
            }
        };
    }

    private static void awaitAndShutdown(CountDownLatch done, ExecutorService pool) {
        try {
            boolean finished = done.await(30, TimeUnit.SECONDS);
            assertTrue(finished, "all threads should finish within the timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            pool.shutdownNow();
        }
    }
}
