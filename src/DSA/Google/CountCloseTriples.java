package DSA.Google;

import java.util.ArrayList;
import java.util.List;

/*
 * Problem:
 * Given three sorted arrays A, B, and C of equal size and an integer D, count
 * index tuples (i, j, k) such that:
 *
 *   |A[i] - B[j]| <= D
 *   |A[i] - C[k]| <= D
 *   |B[j] - C[k]| <= D
 *
 * Equal values at different indices are different choices and must be counted
 * separately.
 *
 * Observation:
 * For three values x, y, and z, all pairwise differences are at most D if
 * and only if:
 *
 *   max(x, y, z) - min(x, y, z) <= D
 *
 * Approach:
 * 1. Merge the three sorted arrays into one sorted list while remembering the
 *    source array of every element.
 * 2. Process the merged list from left to right. The current element is
 *    treated as the rightmost (largest) element of a tuple.
 * 3. Maintain a sliding window containing previously processed values at
 *    least currentValue - D.
 * 4. If the current value came from A, every valid earlier choice must contain
 *    one B and one C from the window, so add countB * countC. Apply the same
 *    logic for elements from B and C.
 *
 * Each tuple is counted exactly once: when its rightmost element in the merged
 * order is processed. This also handles equal values deterministically.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 */
public class CountCloseTriples {

    private static class Entry {
        private final long value;
        private final int source;

        private Entry(long value, int source) {
            this.value = value;
            this.source = source;
        }
    }

    public static long countTuples(long[] a, long[] b, long[] c, long d) {
        if (a == null || b == null || c == null
                || a.length != b.length || b.length != c.length || d < 0) {
            throw new IllegalArgumentException("Invalid input arrays or difference");
        }
        validateSorted(a);
        validateSorted(b);
        validateSorted(c);

        // The merged sequence is sorted, so every earlier element is <= the
        // current element. The current element can therefore be the maximum
        // value of a tuple.
        List<Entry> merged = merge(a, b, c);

        // sourceCounts[0], [1], and [2] store the number of active elements
        // from A, B, and C in the current value window.
        long[] sourceCounts = new long[3];
        int windowStart = 0;
        long answer = 0;

        for (int right = 0; right < merged.size(); right++) {
            Entry current = merged.get(right);

            // Remove values that are too small. Every remaining earlier value
            // is within D of current, so max(value) - min(value) <= D.
            while (merged.get(windowStart).value < current.value - d) {
                sourceCounts[merged.get(windowStart).source]--;
                windowStart++;
            }

            // Use the current element as the choice from its source. Choose
            // one element from each of the other two sources in the window.
            // The current element is added only after counting, so it is not
            // accidentally used twice and cannot be paired with itself.
            if (current.source == 0) {
                answer += sourceCounts[1] * sourceCounts[2];
            } else if (current.source == 1) {
                answer += sourceCounts[0] * sourceCounts[2];
            } else {
                answer += sourceCounts[0] * sourceCounts[1];
            }

            // Future elements may use this element as one of their earlier
            // choices.
            sourceCounts[current.source]++;
        }

        return answer;
    }

    private static List<Entry> merge(long[] a, long[] b, long[] c) {
        List<Entry> merged = new ArrayList<Entry>(a.length + b.length + c.length);
        int i = 0;
        int j = 0;
        int k = 0;

        while (i < a.length || j < b.length || k < c.length) {
            // Select the smallest unmerged value among the three arrays.
            // Since each input is sorted, advancing one pointer preserves
            // sorted order.
            long next = Long.MAX_VALUE;
            if (i < a.length) {
                next = Math.min(next, a[i]);
            }
            if (j < b.length) {
                next = Math.min(next, b[j]);
            }
            if (k < c.length) {
                next = Math.min(next, c[k]);
            }

            // Tie order is fixed, but does not affect the final count.
            if (i < a.length && a[i] == next) {
                merged.add(new Entry(a[i++], 0));
            } else if (j < b.length && b[j] == next) {
                merged.add(new Entry(b[j++], 1));
            } else {
                merged.add(new Entry(c[k++], 2));
            }
        }
        return merged;
    }

    private static void validateSorted(long[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[i - 1]) {
                throw new IllegalArgumentException("Arrays must be sorted");
            }
        }
    }

    public static void main(String[] args) {
        long[] a = {0, 1};
        long[] b = {0, 1};
        long[] c = {0, 1};

        System.out.println(countTuples(a, b, c, 1));
    }
}
