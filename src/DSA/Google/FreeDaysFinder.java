package DSA.Google;

/*
 * Free Days Finder — Difference Array Approach
 *
 * Problem: Given blockers (id, start, end), D days, and k, find all days
 *          where at least k distinct people are FREE.
 *
 * Approach:
 *   1. Group & merge blockers per person (avoid double-counting).
 *   2. For each merged [s, e]: diff[s]++, diff[e+1]--.
 *   3. Prefix sum → busyCount per day → free = total - busy.
 *   4. Collect days where free >= k.
 *
 * Complexity: O(D + B log B)
 */

import java.util.*;

public class FreeDaysFinder {

    public static List<Integer> findFreeDays(int[][] blockers, int D, int k) {
        // Step 1: Group intervals by person, then merge overlapping ones
        Map<Integer, List<int[]>> map = new HashMap<>();
        for (int[] b : blockers) {
            map.computeIfAbsent(b[0], x -> new ArrayList<>()).add(new int[]{b[1], b[2]});
        }

        int totalPeople = map.size();
        int[] diff = new int[D + 2]; // difference array

        // Step 2: For each person's merged intervals, mark in diff array
        for (List<int[]> intervals : map.values()) {
            intervals.sort((a, b) -> a[0] - b[0]);
            int start = intervals.get(0)[0], end = intervals.get(0)[1];
            for (int i = 1; i < intervals.size(); i++) {
                if (intervals.get(i)[0] <= end) {
                    end = Math.max(end, intervals.get(i)[1]);
                } else {
                    diff[start]++;
                    diff[end + 1]--;
                    start = intervals.get(i)[0];
                    end = intervals.get(i)[1];
                }
            }
            diff[start]++;
            diff[end + 1]--;
        }

        // Step 3: Prefix sum to get busy count, collect days where free >= k
        List<Integer> result = new ArrayList<>();
        int busy = 0;
        for (int day = 1; day <= D; day++) {
            busy += diff[day];
            if (totalPeople - busy >= k) {
                result.add(day);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        // blocker format: {id, start, end}
        int[][] blockers1 = {{1, 2, 3}, {2, 5, 6}};
        System.out.println(findFreeDays(blockers1, 6, 2)); // [1, 4]

        int[][] blockers2 = {{1, 1, 3}, {1, 2, 5}, {2, 4, 6}, {3, 1, 2}};
        System.out.println(findFreeDays(blockers2, 7, 2)); // [3, 6, 7]
    }
}
