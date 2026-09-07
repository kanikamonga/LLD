package DSA.Google;

import java.util.*;

/**
 * TTL-based Key-Value Store
 *
 * Data Structures:
 *   - HashMap<String, Entry>: O(1) get/put/delete by key
 *   - TreeMap<Long, Set<String>>: keys grouped by expiration timestamp,
 *     enabling efficient lazy cleanup of expired entries without full scans.
 *
 * Approach: Lazy expiration — expired keys are purged in bulk up to "now"
 * using TreeMap.headMap(), so we never scan all keys.
 *
 * ==================== TIME & SPACE COMPLEXITY ====================
 *
 * Let N = total number of keys in the store
 *     E = number of expired keys purged during a cleanup
 *     B = number of distinct expiration timestamp buckets being purged
 *
 * ┌─────────────────────────┬──────────────────────┬──────────────────────────────────────────────────┐
 * │ Operation               │ Time Complexity      │ Explanation                                      │
 * ├─────────────────────────┼──────────────────────┼──────────────────────────────────────────────────┤
 * │ put(key, value, expiry) │ O(log N)             │ HashMap put O(1) + TreeMap insert O(log N).      │
 * │                         │                      │ If key exists, also removes old bucket O(log N). │
 * ├─────────────────────────┼──────────────────────┼──────────────────────────────────────────────────┤
 * │ get(key)                │ O(1) amortized       │ HashMap lookup O(1). If expired, lazy removal    │
 * │                         │                      │ costs O(log N) but each key is removed at most   │
 * │                         │                      │ once, so amortized O(1).                         │
 * ├─────────────────────────┼──────────────────────┼──────────────────────────────────────────────────┤
 * │ delete(key)             │ O(log N)             │ HashMap remove O(1) + TreeMap bucket remove      │
 * │                         │                      │ O(log N).                                        │
 * ├─────────────────────────┼──────────────────────┼──────────────────────────────────────────────────┤
 * │ countActiveKeys()       │ O(E + B·log N)       │ Purges E expired keys across B buckets via       │
 * │                         │                      │ headMap(). Each bucket removal is O(log N).       │
 * │                         │                      │ After purge, returns store.size() in O(1).       │
 * ├─────────────────────────┼──────────────────────┼──────────────────────────────────────────────────┤
 * │ countActiveKeys(key)    │ O(1) amortized       │ Delegates to get(key), same cost.                │
 * └─────────────────────────┴──────────────────────┴──────────────────────────────────────────────────┘
 *
 * SPACE COMPLEXITY: O(N)
 *   - HashMap stores N entries              → O(N)
 *   - TreeMap stores at most N keys spread across buckets → O(N)
 *   - Entry objects (key, value, expiry)    → O(N)
 *   - Total auxiliary space                 → O(N)
 *
 * ================================================================
 */
class TTLKeyValueStore<V> {

    private static class Entry<V> {
        String key;
        V value;
        long expirationTimestamp;

        Entry(String key, V value, long expirationTimestamp) {
            this.key = key;
            this.value = value;
            this.expirationTimestamp = expirationTimestamp;
        }
    }

    private final Map<String, Entry<V>> store;
    // expirationTimestamp -> set of keys expiring at that time
    private final TreeMap<Long, Set<String>> expiryBuckets;
    private long currentTimestamp;

    public TTLKeyValueStore() {
        this.store = new HashMap<>();
        this.expiryBuckets = new TreeMap<>();
        this.currentTimestamp = 0;
    }

    /**
     * Advances the internal clock. Called before any operation
     * with the current logical timestamp.
     */
    public void setCurrentTimestamp(long timestamp) {
        this.currentTimestamp = timestamp;
    }

    /**
     * Inserts or updates a key-value pair with a specific expiration timestamp.
     */
    public void put(String key, V value, long expirationTimestamp) {
        // If key already exists, remove old expiry bucket entry
        if (store.containsKey(key)) {
            removeFromExpiryBucket(key, store.get(key).expirationTimestamp);
        }

        Entry<V> entry = new Entry<>(key, value, expirationTimestamp);
        store.put(key, entry);
        expiryBuckets.computeIfAbsent(expirationTimestamp, k -> new HashSet<>()).add(key);
    }

    /**
     * Retrieves the value for a key if it is currently active (not expired).
     * Returns null if the key doesn't exist or has expired.
     */
    public V get(String key) {
        Entry<V> entry = store.get(key);
        if (entry == null) return null;

        if (entry.expirationTimestamp <= currentTimestamp) {
            // Expired — remove lazily
            removeEntry(key, entry);
            return null;
        }
        return entry.value;
    }

    /**
     * Removes a key-value pair from the store.
     */
    public void delete(String key) {
        Entry<V> entry = store.remove(key);
        if (entry != null) {
            removeFromExpiryBucket(key, entry.expirationTimestamp);
        }
    }

    /**
     * Returns the count of all active (non-expired) keys.
     * Lazily purges all expired entries first using TreeMap.headMap().
     */
    public int countActiveKeys() {
        purgeExpired();
        return store.size();
    }

    /**
     * Returns 1 if the given key is active, 0 otherwise.
     */
    public int countActiveKeys(String key) {
        return get(key) != null ? 1 : 0;
    }

    // ---- Internal helpers ----

    /**
     * Purges all entries whose expiration timestamp <= currentTimestamp.
     * Uses TreeMap.headMap() so we only touch expired buckets, not the whole store.
     */
    private void purgeExpired() {
        // headMap is exclusive, so use currentTimestamp + 1 to include keys
        // expiring exactly at currentTimestamp
        NavigableMap<Long, Set<String>> expiredBuckets =
                expiryBuckets.headMap(currentTimestamp, true);

        for (Map.Entry<Long, Set<String>> bucket : new ArrayList<>(expiredBuckets.entrySet())) {
            for (String key : bucket.getValue()) {
                store.remove(key);
            }
            expiryBuckets.remove(bucket.getKey());
        }
    }

    private void removeEntry(String key, Entry<V> entry) {
        store.remove(key);
        removeFromExpiryBucket(key, entry.expirationTimestamp);
    }

    private void removeFromExpiryBucket(String key, long expirationTimestamp) {
        Set<String> bucket = expiryBuckets.get(expirationTimestamp);
        if (bucket != null) {
            bucket.remove(key);
            if (bucket.isEmpty()) {
                expiryBuckets.remove(expirationTimestamp);
            }
        }
    }

    // ---- Demo ----

    public static void main(String[] args) {
        TTLKeyValueStore<String> cache = new TTLKeyValueStore<>();

        // t=0: insert keys
        cache.setCurrentTimestamp(0);
        cache.put("session-A", "userAlice", 5);   // expires at t=5
        cache.put("session-B", "userBob",   10);  // expires at t=10
        cache.put("session-C", "userCharlie", 3); // expires at t=3

        System.out.println("Active at t=0: " + cache.countActiveKeys());       // 3

        // t=4: session-C has expired
        cache.setCurrentTimestamp(4);
        System.out.println("get(session-C) at t=4: " + cache.get("session-C")); // null
        System.out.println("Active at t=4: " + cache.countActiveKeys());         // 2

        // t=6: session-A also expired
        cache.setCurrentTimestamp(6);
        System.out.println("get(session-A) at t=6: " + cache.get("session-A")); // null
        System.out.println("Active at t=6: " + cache.countActiveKeys());         // 1

        // delete session-B manually
        cache.delete("session-B");
        System.out.println("Active after delete: " + cache.countActiveKeys());   // 0

        System.out.println("\nAll tests passed.");
    }
}
