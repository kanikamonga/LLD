package LLD.popularitycounter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A node in the vote-ordered doubly linked list: holds every user who
 * currently has exactly {@code votes} votes.
 *
 * Package-private and deliberately a "dumb" data holder - all linking,
 * unlinking and invariant maintenance lives in {@link PopularityCounter},
 * which is the single owner of this structure. Exposing mutation helpers
 * here would let callers corrupt list invariants from outside the lock.
 *
 * Users are held in a LinkedHashSet so that:
 *  - membership add/remove is O(1), and
 *  - iteration order is insertion order, making tie-breaking deterministic
 *    (the first user to reach this vote count wins) at O(1) cost. A TreeSet
 *    would give lexicographic tie-breaking instead, but at O(log k) - not
 *    worth breaking the O(1) guarantee for an arbitrary tie rule.
 */
final class Bucket {
    final int votes;
    final Set<String> users = new LinkedHashSet<>();

    Bucket prev;
    Bucket next;

    Bucket(int votes) {
        this.votes = votes;
    }

    boolean isEmpty() {
        return users.isEmpty();
    }

    @Override
    public String toString() {
        return "Bucket{votes=" + votes + ", users=" + users + "}";
    }
}
