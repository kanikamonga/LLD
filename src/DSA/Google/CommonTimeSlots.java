package DSA.Google;

/*
 * ========================== PROBLEM DESCRIPTION ==========================
 *
 * Common Available Time Slots for N Entities
 *
 * Given a list of available time slots for N entities, find all common time
 * intervals during which ALL N entities are available.
 * Each entity's availability is a list of non-overlapping, sorted intervals.
 *
 * Example:
 *   Entity 1: [[1,5],[10,14],[16,18]]
 *   Entity 2: [[3,6],[12,15]]
 *   Entity 3: [[2,4],[11,13],[17,19]]
 *   Output:   [[3,4],[12,13]]
 *
 * Constraints:
 *   1 ≤ N ≤ 100, 1 ≤ intervals per entity ≤ 1000
 *   0 ≤ start < end ≤ 10^9, intervals sorted and non-overlapping.
 *
 * ========================== SOLUTION APPROACH =============================
 *
 * N-pointer / Sweep approach (like merging N sorted lists):
 *
 * 1. Maintain one pointer per entity, pointing to the current interval.
 * 2. At each step:
 *    - Compute overlap = [max of all starts, min of all ends].
 *    - If overlap is valid (start < end), add it to result.
 *    - Advance the pointer of the entity whose current interval ends earliest
 *      (it cannot contribute to any future overlap).
 * 3. Stop when any entity's pointer goes out of bounds (no more common slots possible).
 *
 * Complexity:
 *   Time:  O(N * T) where T = total intervals across all entities (at most 100*1000).
 *   Space: O(N) for pointers + O(result size).
 *
 * =========================================================================
 */

import java.util.*;

public class CommonTimeSlots {

    public static List<int[]> findCommonSlots(List<List<int[]>> schedules) {
        List<int[]> result = new ArrayList<>();
        int n = schedules.size();
        int[] pointers = new int[n];

        while (true) {
            // Check if any entity has exhausted its intervals
            int maxStart = Integer.MIN_VALUE;
            int minEnd = Integer.MAX_VALUE;
            int minEndIdx = 0;

            boolean valid = true;
            for (int i = 0; i < n; i++) {
                if (pointers[i] >= schedules.get(i).size()) {
                    valid = false;
                    break;
                }
                int[] interval = schedules.get(i).get(pointers[i]);
                maxStart = Math.max(maxStart, interval[0]);
                if (interval[1] < minEnd) {
                    minEnd = interval[1];
                    minEndIdx = i;
                }
            }
            if (!valid) break;

            // If there's a valid overlap, record it
            if (maxStart < minEnd) {
                result.add(new int[]{maxStart, minEnd});
            }

            // Advance the pointer of the entity with the earliest ending interval
            pointers[minEndIdx]++;
        }

        return result;
    }

    public static void main(String[] args) {
        List<List<int[]>> schedules = new ArrayList<>();
        schedules.add(Arrays.asList(new int[]{1, 5}, new int[]{10, 14}, new int[]{16, 18}));
        schedules.add(Arrays.asList(new int[]{3, 6}, new int[]{12, 15}));
        schedules.add(Arrays.asList(new int[]{2, 4}, new int[]{11, 13}, new int[]{17, 19}));

        List<int[]> result = findCommonSlots(schedules);
        System.out.print("[");
        for (int i = 0; i < result.size(); i++) {
            System.out.print(Arrays.toString(result.get(i)));
            if (i < result.size() - 1) System.out.print(", ");
        }
        System.out.println("]");

        // Test 2: Two entities
        List<List<int[]>> schedules2 = new ArrayList<>();
        schedules2.add(Arrays.asList(new int[]{0, 2}, new int[]{5, 10}, new int[]{16, 20}));
        schedules2.add(Arrays.asList(new int[]{1, 5}, new int[]{10, 15}, new int[]{18, 22}));

        List<int[]> result2 = findCommonSlots(schedules2);
        System.out.print("[");
        for (int i = 0; i < result2.size(); i++) {
            System.out.print(Arrays.toString(result2.get(i)));
            if (i < result2.size() - 1) System.out.print(", ");
        }
        System.out.println("]");
    }
}
