package DSA.Amazon;

/*
 * Problem:
 * Choose a value x and earn x points for every occurrence of x. After choosing
 * x, all occurrences of x, x - 1, and x + 1 are deleted.
 *
 * Values x - 1 and x + 1 cannot both be chosen with x. Therefore, after
 * aggregating equal values, the problem becomes the House Robber problem:
 *
 *   choose total[x] or skip it
 *
 * If x is chosen, x - 1 and x + 1 cannot be chosen.
 *
 * Example:
 * nums = [3, 4, 2, 3, 3, 4]
 * total[2] = 2
 * total[3] = 9
 * total[4] = 8
 *
 * Choose 4 and 2: 8 + 2 = 10.
 *
 * Time Complexity: O(n + maxValue)
 * Space Complexity: O(maxValue)
 */
public class DeleteAndEarn {

    public static long maximumPoints(int[] nums) {
        if (nums == null) {
            throw new IllegalArgumentException("Array cannot be null");
        }
        if (nums.length == 0) {
            return 0;
        }

        int maxValue = 0;
        for (int value : nums) {
            maxValue = Math.max(maxValue, value);
        }

        long[] points = new long[maxValue + 1];
        for (int value : nums) {
            points[value] += value;
        }

        long bestWithoutPreviousValue = 0;
        long bestWithPreviousValue = 0;

        for (int value = 1; value <= maxValue; value++) {
            // If we choose this value, value - 1 cannot have been chosen.
            long bestIfChooseCurrent = bestWithoutPreviousValue + points[value];

            // If we skip this value, keep the better result from value - 1.
            long bestIfSkipCurrent = Math.max(
                    bestWithoutPreviousValue, bestWithPreviousValue);

            bestWithoutPreviousValue = bestIfSkipCurrent;
            bestWithPreviousValue = bestIfChooseCurrent;
        }

        return Math.max(bestWithoutPreviousValue, bestWithPreviousValue);
    }

    public static void main(String[] args) {
        int[] nums = {3, 4, 2, 3, 3, 4};
        System.out.println(maximumPoints(nums)); // 10
    }
}
