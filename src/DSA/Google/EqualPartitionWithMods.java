package DSA.Google;

import java.util.Arrays;

/**
 * Equal Partition with at most K Modifications
 *
 * Given an array of integers and an integer K, determine if we can partition
 * the array into two subsets with equal sums by changing at most K elements
 * to any integer value.
 *
 * ==================== APPROACH ====================
 *
 * Key Insight: We can change up to K elements to ANY value. When we change
 * an element, we effectively remove it from the sum constraint and can
 * redistribute its contribution freely between the two subsets.
 *
 * Let S = total sum of the array, and suppose we pick K elements to modify.
 * Let R = sum of the remaining (N - K) elements.
 *
 * Case 1: K >= 2
 *   Always TRUE. We can set two modified elements to arbitrarily large
 *   values (+X and -X) to adjust the balance of any partition.
 *   Alternatively, place one huge value in each subset to balance them.
 *
 * Case 2: K == 1
 *   We remove one element (value v) and replace it with any value.
 *   Remaining sum R = S - v. We need to split all N elements into two
 *   equal-sum subsets. The modified element contributes some value w
 *   to whichever subset it joins.
 *   - If R is even: we can set w = 0, and check if the remaining N-1
 *     elements can be partitioned into two subsets with equal sum R/2.
 *     But even if they can't, we can adjust w to compensate the difference.
 *     Specifically, if subset1 has sum A and subset2 has sum R-A among
 *     the remaining elements, placing the modified element (value w) in
 *     subset1 gives: A + w = R - A → w = R - 2A. Always solvable.
 *   - If R is odd: place modified element in subset1 with value w.
 *     Need A + w = R - A → w = R - 2A. Total sum = R + w = 2R - 2A,
 *     which must be even → 2R - 2A is always even. So it works!
 *   Actually with K=1 we can ALWAYS make it work:
 *     Total = R + w. For equal partition: each subset = (R + w) / 2.
 *     We choose w so that (R + w) is even and one subset sums correctly.
 *     Place modified element in the smaller-sum subset. Set w = |sum1 - sum2|.
 *     This always works for any partition of the remaining elements.
 *   → TRUE when N >= 2 (need at least 2 subsets to be non-empty? No,
 *     subsets can be empty). Actually TRUE always when K >= 1 and N >= 1.
 *
 *   Wait — let's be more careful. With K=1 and N=1: array has 1 element,
 *   we change it. Two subsets: {w} and {}. Sums: w and 0. Need w = 0.
 *   We CAN set w = 0 → TRUE.
 *
 * Case 3: K == 0 (no modifications allowed)
 *   Classic Equal Subset Sum Partition problem.
 *   - If S is odd → FALSE (can't split odd sum equally).
 *   - If S is even → DP to check if a subset sums to S/2.
 *   - Use bitset-based DP for efficiency.
 *
 * ==================== COMPLEXITY ====================
 *
 * ┌──────────────┬────────────────────────┬──────────────────────────────────────┐
 * │ Case         │ Time                   │ Space                                │
 * ├──────────────┼────────────────────────┼──────────────────────────────────────┤
 * │ K >= 1       │ O(1)                   │ O(1)                                 │
 * ├──────────────┼────────────────────────┼──────────────────────────────────────┤
 * │ K == 0       │ O(N * S/2)             │ O(S/2) using 1D DP                   │
 * │              │ where S = sum of |arr| │ (boolean dp array of size S/2 + 1)   │
 * └──────────────┴────────────────────────┴──────────────────────────────────────┘
 *
 * =================================================================
 */
class EqualPartitionWithMods {

    /**
     * Returns true if the array can be partitioned into two equal-sum subsets
     * after modifying at most K elements to any value.
     */
    public static boolean canPartition(int[] arr, int k) {
        int n = arr.length;

        if (n == 0) return true; // two empty subsets both sum to 0

        // With even 1 modification we can always balance two subsets:
        // Change one element's value to compensate any difference.
        if (k >= 1) return true;

        // K == 0: classic equal subset sum partition
        int sum = 0;
        for (int val : arr) sum += val;

        if (sum % 2 != 0) return false;

        int target = sum / 2;

        // Standard 1D subset-sum DP
        boolean[] dp = new boolean[target + 1];
        dp[0] = true;

        for (int val : arr) {
//            if (val > target) continue;
            // Traverse right-to-left to avoid using same element twice
            for (int j = target; j >= val; j--) {
                if (dp[j - val]) {
                    dp[j] = true;
                }
            }
        }

        return dp[target];
    }

    // ---- Demo ----

    public static void main(String[] args) {
        // Test 1: K=0, partitionable
        System.out.println("=== Test 1: K=0, [1,5,11,5] ===");
        assert canPartition(new int[]{1, 5, 11, 5}, 0) == true
                : "{1,5,5} and {11} both sum to 11";
        System.out.println("PASS: true");

        // Test 2: K=0, not partitionable
        System.out.println("=== Test 2: K=0, [1,2,3,5] ===");
        assert canPartition(new int[]{1, 2, 3, 5}, 0) == false
                : "Sum=11, odd → impossible";
        System.out.println("PASS: false");

        // Test 3: K=1, odd sum becomes partitionable
        System.out.println("=== Test 3: K=1, [1,2,3,5] ===");
        assert canPartition(new int[]{1, 2, 3, 5}, 1) == true
                : "Can modify one element to balance";
        System.out.println("PASS: true");

        // Test 4: K=2, always true
        System.out.println("=== Test 4: K=2, [100,1,1,1] ===");
        assert canPartition(new int[]{100, 1, 1, 1}, 2) == true;
        System.out.println("PASS: true");

        // Test 5: K=0, even sum but not partitionable
        System.out.println("=== Test 5: K=0, [1,1,1,1,1,1,1,3] ===");
        // sum = 10, target = 5, subsets: {3,1,1} and {1,1,1,1} → yes
        assert canPartition(new int[]{1, 1, 1, 1, 1, 1, 1, 3}, 0) == true;
        System.out.println("PASS: true");

        // Test 6: K=0, [3,3,3,3,3] sum=15 odd → false
        System.out.println("=== Test 6: K=0, [3,3,3,3,3] ===");
        assert canPartition(new int[]{3, 3, 3, 3, 3}, 0) == false;
        System.out.println("PASS: false");

        // Test 7: empty array
        System.out.println("=== Test 7: empty array ===");
        assert canPartition(new int[]{}, 0) == true;
        System.out.println("PASS: true");

        // Test 8: single element, K=0
        System.out.println("=== Test 8: K=0, [0] ===");
        assert canPartition(new int[]{14,9,8,4,3,2}, 0) == true;
        System.out.println("PASS: true");

        // Test 9: single element non-zero, K=0
        System.out.println("=== Test 9: K=0, [5] ===");
        assert canPartition(new int[]{5}, 0) == false;
        System.out.println("PASS: false");

        System.out.println("\nAll tests passed.");
    }
}
