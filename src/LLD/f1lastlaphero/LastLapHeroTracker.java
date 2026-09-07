package LLD.f1lastlaphero;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks lap times for many drivers and answers "who is the Last Lap Hero
 * right now?" efficiently as a live stream of (driverId, lapTime) events
 * arrives.
 *
 * -------------------------------------------------------------------------
 * Why not just scan all drivers on every query?
 * -------------------------------------------------------------------------
 * A naive approach - store each driver's stats in a HashMap and, on every
 * getLastLapHero() call, iterate all drivers to find the max gain - is
 * O(D) per query where D = number of drivers (up to 10^4). If heroes are
 * queried frequently (e.g. after every single lap update, which is a very
 * plausible access pattern for a live leaderboard), that degrades to O(events
 * * drivers) overall.
 *
 * Instead we maintain a SECOND index, sorted by gain, so:
 *   - updateLap(driverId, lapTime): O(log D)  - remove driver's old gain
 *     entry (if any) from the sorted index, recompute, reinsert.
 *   - getLastLapHero(): O(log D) (or O(1) with a cached "last" pointer) -
 *     read the max entry of the sorted index directly.
 *
 * Data structures:
 *  - driverStats: driverId -> DriverStats (running totals + last lap + gain)
 *  - gainIndex: TreeMap<gain, TreeSet<driverId>> acting as a sorted
 *    multiset keyed by gain (multiple drivers can tie on the same gain,
 *    hence a TreeSet of ids per gain bucket rather than a single id).
 *    TreeMap keeps entries sorted by key, so lastEntry() gives the maximum
 *    gain in O(log D), and floor/ceiling-style bucket lookups let us remove
 *    a specific driver's stale entry in O(log D) too.
 *
 * Tie-breaking: if multiple drivers share the exact maximum gain, the driver
 * with the lexicographically smallest id is returned, for determinism.
 *
 * Thread-safety: a single lock guards the (stats + index) pair so a read
 * never observes them out of sync with each other (e.g. mid-update, where
 * the old index entry has been removed but the new one not yet inserted).
 * Per-driver stats mutation and index maintenance are cheap, so a single
 * lock (rather than per-driver locks) keeps this simple and correct; this
 * is adequate because get/update calls are O(log D), not O(D).
 *
 * -------------------------------------------------------------------------
 * Design patterns applied
 * -------------------------------------------------------------------------
 * - Strategy ({@link GainStrategy}): the gain FORMULA is injected rather
 *   than hardcoded, since the problem itself flags the formula as a
 *   variation point. This class's job (indexing/lookup/concurrency) stays
 *   fixed regardless of which formula is plugged in.
 * - Observer ({@link HeroChangeListener}): external consumers (a live
 *   leaderboard UI, commentary feed, etc.) register once and get pushed a
 *   notification only when the hero actually changes, rather than polling
 *   getLastLapHero() after every update. Decouples "who is the hero"
 *   bookkeeping from "what happens when the hero changes".
 */
public final class LastLapHeroTracker {

    private final Map<String, DriverStats> driverStats = new ConcurrentHashMap<>();
    private final TreeMap<Double, TreeSet<String>> gainIndex = new TreeMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final GainStrategy gainStrategy;
    // CopyOnWriteArrayList: listener registration is rare, notification happens on
    // every updateLap() call, so we want cheap/lock-free iteration over occasional writes.
    private final List<HeroChangeListener> listeners = new CopyOnWriteArrayList<>();
    private String currentHero = null;
    // Cached gain of currentHero. Lets updateLap() usually decide the new hero in O(1)
    // (see updateLap()) instead of always paying for a TreeMap.lastEntry() traversal.
    private double currentMaxGain = Double.NEGATIVE_INFINITY;

    public LastLapHeroTracker() {
        this(new AverageDeltaGainStrategy());
    }

    public LastLapHeroTracker(GainStrategy gainStrategy) {
        if (gainStrategy == null) {
            throw new IllegalArgumentException("gainStrategy must not be null");
        }
        this.gainStrategy = gainStrategy;
    }

    /** Registers an observer to be notified whenever the Last Lap Hero changes. */
    public void addHeroChangeListener(HeroChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeHeroChangeListener(HeroChangeListener listener) {
        listeners.remove(listener);
    }

    /** Records that {@code driverId} just completed a lap in {@code lapTime}. */
    public void updateLap(String driverId, int lapTime) {
        if (driverId == null || driverId.isBlank()) {
            throw new IllegalArgumentException("driverId must not be blank");
        }
        if (lapTime <= 0) {
            throw new IllegalArgumentException("lapTime must be > 0");
        }

        String previousHero;
        String newHero;

        lock.lock();
        try {
            DriverStats stats = driverStats.computeIfAbsent(driverId, id -> new DriverStats(id, gainStrategy));

            // Remove this driver's stale gain entry (if it has recorded a lap before)
            // from the sorted index BEFORE recomputing, otherwise we'd leave a dangling
            // entry under the old gain value.
            if (stats.getTotalLaps() > 0) {
                removeFromIndex(stats.getGain(), driverId);
            }

            stats.recordLap(lapTime);
            double newGain = stats.getGain();

            insertIntoIndex(newGain, driverId);

            previousHero = currentHero;

            // --- Incremental hero maintenance: avoid a TreeMap traversal whenever possible ---
            //
            // Fast path 1 (O(1)): this driver's new gain strictly beats the cached max, OR
            // ties it while being the lexicographically smaller id (our tie-break rule) ->
            // it's unambiguously the new hero, regardless of who held the title before.
            //
            // Fast path 2 (O(1)): some OTHER driver updated but didn't beat the cached max ->
            // the cached hero is untouched, nothing to recompute.
            //
            // Slow path (O(log D)): only needed when the driver being updated IS the
            // current hero and its new gain no longer strictly exceeds the old max (it
            // may have regressed, or exactly tied - both require consulting the index to
            // find the true max/tie-break winner, since we no longer know how other
            // drivers compare without looking).
            if (newGain > currentMaxGain || (newGain == currentMaxGain && driverId.compareTo(currentHero) < 0)) {
                currentHero = driverId;
                currentMaxGain = newGain;
            } else if (driverId.equals(currentHero)) {
                Map.Entry<Double, TreeSet<String>> maxEntry = gainIndex.lastEntry();
                currentMaxGain = maxEntry.getKey();
                currentHero = maxEntry.getValue().first();
            }
            // else: some other, non-hero driver updated without beating the max - no-op.

            newHero = currentHero;
        } finally {
            lock.unlock();
        }

        // Notify observers OUTSIDE the lock: listener code is untrusted/unknown
        // (could be slow or itself call back into the tracker), so we must not
        // hold our internal lock while invoking it - avoids blocking other
        // threads' updateLap()/getLastLapHero() calls and avoids deadlock risk.
        if (!newHero.equals(previousHero)) {
            for (HeroChangeListener listener : listeners) {
                listener.onHeroChanged(previousHero, newHero);
            }
        }
    }

    /**
     * @return the driverId with the maximum performance gain
     *         (per the configured {@link GainStrategy}) recorded so far.
     * @throws NoLapDataException if no laps have been recorded yet.
     */
    public String getLastLapHero() {
        lock.lock();
        try {
            if (currentHero == null) {
                throw new NoLapDataException("No lap data has been recorded yet");
            }
            return currentHero; // O(1): maintained incrementally by updateLap()
        } finally {
            lock.unlock();
        }
    }

    /** Read-only snapshot of a driver's current stats, or null if unknown. */
    public DriverStatsView getDriverStats(String driverId) {
        lock.lock();
        try {
            DriverStats s = driverStats.get(driverId);
            if (s == null) return null;
            return new DriverStatsView(s.getDriverId(), s.getTotalLaps(), s.getAverageLapTime(),
                    s.getLastLapTime(), s.getGain());
        } finally {
            lock.unlock();
        }
    }

    private void insertIntoIndex(double gain, String driverId) {
        gainIndex.computeIfAbsent(gain, g -> new TreeSet<>()).add(driverId);
    }

    private void removeFromIndex(double gain, String driverId) {
        TreeSet<String> bucket = gainIndex.get(gain);
        if (bucket == null) return; // defensive; shouldn't happen given call sites
        bucket.remove(driverId);
        if (bucket.isEmpty()) {
            gainIndex.remove(gain);
        }
    }

    /** Immutable snapshot returned to callers so internal mutable state is never leaked. */
    public record DriverStatsView(String driverId, int totalLaps, double averageLapTime,
                                   int lastLapTime, double gain) {
    }
}

