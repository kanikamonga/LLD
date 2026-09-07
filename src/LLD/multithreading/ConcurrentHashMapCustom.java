package LLD.multithreading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A simplified, from-scratch ConcurrentHashMap using SEGMENTED LOCKING
 * (the design used by Java 7's ConcurrentHashMap before Java 8 switched to
 * per-bucket synchronized/CAS with a single backing array).
 *
 * Design goals / reasoning:
 * -------------------------
 * 1. Why not just synchronize every method on one lock?
 *    -> That would serialize ALL reads/writes across the whole map, killing
 *       concurrency. We want writers to different regions of the map to
 *       proceed in parallel, and reads to never block at all.
 *
 * 2. Why segments?
 *    -> Split the map into N independent "Segment" objects, each an
 *       independent hash table with its OWN lock. A key always maps to
 *       exactly one segment (via high bits of its hash), so only threads
 *       writing to the SAME segment ever contend with each other.
 *       Concurrency level (segment count) bounds worst-case write contention.
 *
 * 3. Why are reads lock-free?
 *    -> Entry.value and the table array reference are volatile, so a reader
 *       always sees a fully-published (safely constructed) Entry/table from
 *       another thread's completed write (happens-before via volatile write
 *       in put() -> volatile read in get()). Readers never take the segment
 *       lock, so gets never block behind a writer and never block each other.
 *
 * 4. Why per-segment resizing instead of one global resize?
 *    -> Resizing is the expensive/blocking part of a hash map. Confining it
 *       to a single segment means a resize only pauses writers to THAT
 *       segment (briefly, under its lock), not the entire map.
 */
public class ConcurrentHashMapCustom<K, V> {

    /** Immutable-ish link-list node. Value is volatile so writes are visible without locking on read. */
    static final class Entry<K, V> {
        final int hash;
        final K key;
        volatile V value;
        volatile Entry<K, V> next;

        Entry(int hash, K key, V value, Entry<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    /**
     * One independently-locked shard of the map. Extends ReentrantLock so each
     * segment IS its own lock (avoids one extra object indirection per segment).
     */
    static final class Segment<K, V> extends ReentrantLock {
        // volatile: writers publish a new array reference after resize; readers
        // must see the latest array without acquiring the lock.
        volatile Entry<K, V>[] table;
        volatile int count;           // number of entries currently in this segment
        int threshold;                // resize trigger: count > threshold
        final float loadFactor;

        @SuppressWarnings("unchecked")
        Segment(int initialCapacity, float loadFactor) {
            this.loadFactor = loadFactor;
            this.table = (Entry<K, V>[]) new Entry[initialCapacity];
            this.threshold = (int) (initialCapacity * loadFactor);
        }

        /** Lock-free read path: no lock acquired, relies on volatile visibility. */
        V get(Object key, int hash) {
            Entry<K, V>[] tab = table; // single volatile read, then work with local snapshot
            int index = hash & (tab.length - 1);
            for (Entry<K, V> e = tab[index]; e != null; e = e.next) {
                if (e.hash == hash && e.key.equals(key)) {
                    return e.value;
                }
            }
            return null;
        }

        /** Write path: must hold this segment's lock before calling. */
        V put(K key, int hash, V value, boolean onlyIfAbsent) {
            lock();
            try {
                Entry<K, V>[] tab = table;
                int index = hash & (tab.length - 1);
                Entry<K, V> first = tab[index];

                for (Entry<K, V> e = first; e != null; e = e.next) {
                    if (e.hash == hash && e.key.equals(key)) {
                        V old = e.value;
                        if (!onlyIfAbsent) {
                            e.value = value; // volatile write -> visible to lock-free readers
                        }
                        return old;
                    }
                }

                // Key not found: insert new node at head of the chain.
                Entry<K, V> newEntry = new Entry<>(hash, key, value, first);
                tab[index] = newEntry;
                int newCount = count + 1;
                count = newCount; // volatile write published AFTER the entry is fully linked in
                if (newCount > threshold) {
                    resize();
                }
                return null;
            } finally {
                unlock();
            }
        }

        /** Write path: must hold this segment's lock before calling. */
        V remove(Object key, int hash) {
            lock();
            try {
                Entry<K, V>[] tab = table;
                int index = hash & (tab.length - 1);
                Entry<K, V> first = tab[index];

                Entry<K, V> prev = null;
                for (Entry<K, V> e = first; e != null; e = e.next) {
                    if (e.hash == hash && e.key.equals(key)) {
                        if (prev == null) {
                            tab[index] = e.next;
                        } else {
                            prev.next = e.next;
                        }
                        count = count - 1;
                        return e.value;
                    }
                    prev = e;
                }
                return null;
            } finally {
                unlock();
            }
        }

        /** Doubles this segment's table and re-links entries. Caller must hold the lock. */
        @SuppressWarnings("unchecked")
        private void resize() {
            Entry<K, V>[] oldTable = table;
            int oldCapacity = oldTable.length;
            int newCapacity = oldCapacity * 2;
            Entry<K, V>[] newTable = (Entry<K, V>[]) new Entry[newCapacity];

            for (int i = 0; i < oldCapacity; i++) {
                Entry<K, V> e = oldTable[i];
                while (e != null) {
                    Entry<K, V> next = e.next;
                    int idx = e.hash & (newCapacity - 1);
                    // Rebuild chain (new node not strictly required here since Segment
                    // is single-writer under lock, but we keep entries immutable-ish
                    // for clarity; reusing nodes is also safe since only 'next' changes
                    // and readers re-read the volatile table/next each time).
                    e.next = newTable[idx];
                    newTable[idx] = e;
                    e = next;
                }
            }

            threshold = (int) (newCapacity * loadFactor);
            table = newTable; // single volatile write: publishes the whole new table atomically to readers
        }

        int size() {
            return count;
        }
    }

    private final Segment<K, V>[] segments;
    private final int segmentMask;   // segments.length - 1 (segments.length is a power of 2)
    private final int segmentShift;  // bits to shift hash right to pick a segment

    @SuppressWarnings("unchecked")
    public ConcurrentHashMapCustom(int concurrencyLevel, int initialCapacityPerSegment, float loadFactor) {
        // Round concurrencyLevel up to next power of 2 so we can use bit-masking instead of modulo.
        int segShift = 0;
        int ssize = 1;
        while (ssize < concurrencyLevel) {
            ssize <<= 1;
            segShift++;
        }
        this.segmentShift = 32 - segShift;
        this.segmentMask = ssize - 1;

        this.segments = (Segment<K, V>[]) new Segment[ssize];
        for (int i = 0; i < ssize; i++) {
            segments[i] = new Segment<>(initialCapacityPerSegment, loadFactor);
        }
    }

    public ConcurrentHashMapCustom() {
        this(16, 16, 0.75f);
    }

    /** Spreads bits of hashCode() to reduce collisions when segment/table sizes are powers of 2. */
    private static int hash(Object key) {
        int h = key.hashCode();
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    private Segment<K, V> segmentFor(int hash) {
        // Use the HIGH bits of the hash to choose a segment, and the LOW bits (inside
        // the segment) to choose a bucket. This keeps segment selection and bucket
        // selection statistically independent, spreading keys evenly across segments.
        return segments[(hash >>> segmentShift) & segmentMask];
    }

    public V get(K key) {
        int h = hash(key);
        return segmentFor(h).get(key, h); // lock-free
    }

    public V put(K key, V value) {
        int h = hash(key);
        return segmentFor(h).put(key, h, value, false);
    }

    public V putIfAbsent(K key, V value) {
        int h = hash(key);
        return segmentFor(h).put(key, h, value, true);
    }

    public V remove(K key) {
        int h = hash(key);
        return segmentFor(h).remove(key, h);
    }

    public boolean containsKey(K key) {
        int h = hash(key);
        return segmentFor(h).get(key, h) != null;
    }

    /** Approximate size (sums per-segment counts without a global lock). */
    public int size() {
        int total = 0;
        for (Segment<K, V> s : segments) {
            total += s.size();
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Demo: concurrent writers hammering the map, then verified read-back.
    // ------------------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMapCustom<Integer, String> map = new ConcurrentHashMapCustom<>(8, 16, 0.75f);

        final int THREADS = 8;
        final int KEYS_PER_THREAD = 5_000;
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int base = t * KEYS_PER_THREAD;
            pool.submit(() -> {
                for (int i = 0; i < KEYS_PER_THREAD; i++) {
                    int key = base + i;
                    map.put(key, "value-" + key);
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        int expected = THREADS * KEYS_PER_THREAD;
        System.out.println("Expected size: " + expected + ", actual size: " + map.size());

        // Spot-check a few keys
        System.out.println("get(0) = " + map.get(0));
        System.out.println("get(expected-1) = " + map.get(expected - 1));
        System.out.println("containsKey(999999) = " + map.containsKey(999_999));

        map.remove(0);
        System.out.println("after remove(0), get(0) = " + map.get(0) + ", size = " + map.size());
    }
}
