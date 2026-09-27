package LLD.popularitycounter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe popularity counter supporting O(1) add/remove/increment/decrement
 * and O(1) "who has the most votes?".
 *
 * =========================================================================
 * WHY THIS DATA STRUCTURE
 * =========================================================================
 * The naive design is {@code Map<String,Integer> votes} plus a scan to find
 * the maximum: O(1) updates but O(N) per getUserWithMostVote() - bad if the
 * leaderboard is polled frequently (the expected access pattern).
 *
 * The next obvious design is a sorted index, e.g. TreeMap<votes, users>:
 * O(log N) for everything.
 *
 * But this problem has a property the TreeMap design doesn't exploit:
 * VOTES ONLY EVER CHANGE BY EXACTLY +/-1. So a user never jumps to an
 * arbitrary vote count - they always move to an ADJACENT one. That means if
 * we keep vote-count buckets in a doubly linked list sorted by vote count,
 * an increment is just "move this user to bucket.next" (creating it in place
 * if the neighbouring count doesn't exist yet) - a pure pointer operation,
 * no search, O(1). The maximum is always the last non-empty bucket, reachable
 * in O(1) from the tail sentinel.
 *
 *   head <-> (votes=-2) <-> (votes=0)* <-> (votes=1) <-> (votes=5) <-> tail
 *   (sentinel)              ^ permanent                                (sentinel)
 *
 * Each bucket holds the SET of users at that exact vote count, and a
 * side map userToBucket gives O(1) "where is this user right now?".
 *
 * =========================================================================
 * DESIGN DECISIONS WORTH CALLING OUT
 * =========================================================================
 * 1. Negative vote counts are ALLOWED (a net-downvoted user can go below
 *    zero, as on Reddit-style systems). decrementVote() is therefore the
 *    exact mirror of incrementVote() with no clamping special case.
 *
 * 2. The zero bucket is PERMANENT (never unlinked, even when empty). This is
 *    what keeps addUser() O(1): a newly added user starts at 0 votes, and if
 *    the zero bucket could be garbage-collected we'd have to *search* the
 *    sorted list for where to re-insert it (O(N)) whenever negative buckets
 *    exist below it. Keeping it pinned means addUser() is always a direct
 *    O(1) insert. The cost is one possibly-empty bucket, and the invariant
 *    "the zero bucket is the ONLY bucket that may be empty" (every other
 *    bucket is unlinked the moment its last user leaves), which in turn
 *    means findTopBucket() skips at most one empty node - still O(1).
 *
 * 3. Tie-breaking: among users tied at the maximum, the one who reached that
 *    count earliest is returned (LinkedHashSet insertion order) - O(1) and
 *    deterministic.
 *
 * =========================================================================
 * CONCURRENCY
 * =========================================================================
 * Writes (add/remove/increment/decrement) take a single ReentrantLock.
 *
 * Why one global lock rather than fine-grained/striped locking? Because
 * every mutation relinks a shared doubly linked list; correct fine-grained
 * locking over a linked structure needs hand-over-hand locking and is both
 * error-prone and easy to deadlock. Crucially, it would buy very little
 * here: every critical section is O(1) - a handful of pointer writes and
 * hash lookups - so lock HOLD TIME is already minimal. Short critical
 * sections under one lock generally beat long ones under many locks.
 *
 * Why not a ReentrantReadWriteLock? RW locks pay off when read critical
 * sections are long enough to amortise their extra bookkeeping. Our reads
 * are O(1), so the RW overhead would likely cost more than it saves.
 *
 * Instead, reads are made lock-free a different way: the current top user is
 * maintained INCREMENTALLY and cached in a volatile field, refreshed at the
 * end of every mutation (which already holds the lock, so the cache can
 * never be updated concurrently). getUserWithMostVote() is then a single
 * volatile read - no lock acquisition at all, so a hot polling leaderboard
 * never contends with writers.
 *
 * Staleness note: a lock-free read may observe a value that a concurrent
 * in-flight write is about to change. That is inherent to concurrent
 * snapshot reads - even a fully locked read would be stale the instant it
 * returns - so this weakens nothing in practice. Each returned value is a
 * genuine point-in-time state of the counter, never a torn/corrupt one.
 */
public final class PopularityCounter {

    /** userId -> the bucket currently holding that user. */
    private final Map<String, Bucket> userToBucket = new HashMap<>();

    /** Sentinels; never hold users. Identity (not vote value) is used to detect list ends. */
    private final Bucket head = new Bucket(Integer.MIN_VALUE);
    private final Bucket tail = new Bucket(Integer.MAX_VALUE);

    /** The pinned zero bucket - see design note 2. */
    private final Bucket zeroBucket = new Bucket(0);

    private final ReentrantLock lock = new ReentrantLock();

    /** Incrementally maintained cache of the current most-voted user; null when no users. */
    private volatile String topUser = null;

    public PopularityCounter() {
        head.next = zeroBucket;
        zeroBucket.prev = head;
        zeroBucket.next = tail;
        tail.prev = zeroBucket;
    }

    /**
     * Registers a new user with 0 votes.
     *
     * @throws DuplicateUserException if the user is already tracked
     */
    public void addUser(String userId) {
        validateUserId(userId);
        lock.lock();
        try {
            if (userToBucket.containsKey(userId)) {
                throw new DuplicateUserException(userId);
            }
            zeroBucket.users.add(userId);
            userToBucket.put(userId, zeroBucket);
            refreshTopUser();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a user and their votes entirely.
     *
     * @throws UserNotFoundException if the user is not tracked
     */
    public void removeUser(String userId) {
        validateUserId(userId);
        lock.lock();
        try {
            Bucket bucket = userToBucket.remove(userId);
            if (bucket == null) {
                throw new UserNotFoundException(userId);
            }
            bucket.users.remove(userId);
            unlinkIfEmpty(bucket);
            refreshTopUser();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds one vote for the user.
     *
     * @throws UserNotFoundException if the user is not tracked
     */
    public void incrementVote(String userId) {
        validateUserId(userId);
        lock.lock();
        try {
            Bucket current = requireBucket(userId);
            int targetVotes = current.votes + 1;

            // The target bucket is either already the immediate next node, or must be
            // created and spliced in right there - either way O(1), no search needed.
            Bucket target = (current.next != tail && current.next.votes == targetVotes)
                    ? current.next
                    : linkAfter(current, new Bucket(targetVotes));

            moveUser(userId, current, target);
            refreshTopUser();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes one vote from the user. Vote counts may go negative (see design note 1).
     *
     * @throws UserNotFoundException if the user is not tracked
     */
    public void decrementVote(String userId) {
        validateUserId(userId);
        lock.lock();
        try {
            Bucket current = requireBucket(userId);
            int targetVotes = current.votes - 1;

            Bucket target = (current.prev != head && current.prev.votes == targetVotes)
                    ? current.prev
                    : linkAfter(current.prev, new Bucket(targetVotes));

            moveUser(userId, current, target);
            refreshTopUser();
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the userId with the most votes (ties broken by who reached that count first)
     * @throws NoUsersException if no users are currently tracked
     */
    public String getUserWithMostVote() {
        String top = topUser; // single volatile read - lock-free, O(1)
        if (top == null) {
            throw new NoUsersException("No users are being tracked");
        }
        return top;
    }

    /**
     * @return the current vote count for a user
     * @throws UserNotFoundException if the user is not tracked
     */
    public int getVotes(String userId) {
        validateUserId(userId);
        lock.lock();
        try {
            return requireBucket(userId).votes;
        } finally {
            lock.unlock();
        }
    }

    /** @return number of tracked users. */
    public int userCount() {
        lock.lock();
        try {
            return userToBucket.size();
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Internals - all callers below already hold the lock.
    // ------------------------------------------------------------------

    private Bucket requireBucket(String userId) {
        Bucket bucket = userToBucket.get(userId);
        if (bucket == null) {
            throw new UserNotFoundException(userId);
        }
        return bucket;
    }

    /** Moves a user between buckets, dropping the source bucket if it became empty. */
    private void moveUser(String userId, Bucket from, Bucket to) {
        from.users.remove(userId);
        to.users.add(userId);
        userToBucket.put(userId, to);
        unlinkIfEmpty(from);
    }

    /** Splices {@code newBucket} immediately after {@code node} and returns it. */
    private Bucket linkAfter(Bucket node, Bucket newBucket) {
        newBucket.prev = node;
        newBucket.next = node.next;
        node.next.prev = newBucket;
        node.next = newBucket;
        return newBucket;
    }

    /**
     * Drops a bucket once its last user leaves, so the list only ever contains
     * vote counts that actually have users. The zero bucket is exempt - see
     * design note 2; that exemption is what guarantees at most one empty bucket.
     */
    private void unlinkIfEmpty(Bucket bucket) {
        if (bucket == zeroBucket || !bucket.isEmpty()) {
            return;
        }
        bucket.prev.next = bucket.next;
        bucket.next.prev = bucket.prev;
        bucket.prev = null;
        bucket.next = null;
    }

    /**
     * Recomputes the cached top user. O(1): walks back from the tail past at
     * most one empty bucket, since the pinned zero bucket is the only bucket
     * that is ever allowed to be empty.
     */
    private void refreshTopUser() {
        Bucket bucket = tail.prev;
        while (bucket != head && bucket.isEmpty()) {
            bucket = bucket.prev;
        }
        topUser = (bucket == head) ? null : bucket.users.iterator().next();
    }

    private static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }
    }
}
