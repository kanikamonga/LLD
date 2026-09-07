package DSA.Google;

/*
 * ========================== PROBLEM DESCRIPTION ==========================
 *
 * Painting a Fence with Minimum Strokes
 *
 * Given n planks with heights a[i], paint the entire fence using a 1m-wide brush.
 * Strokes can be vertical (full height of one plank) or horizontal (continuous
 * run across adjacent planks at the same row). Find the minimum number of strokes.
 *
 * Example 1: n=5, a=[2,2,1,2,1] → 3
 *   - 1 horizontal stroke at row 1 across all 5 planks
 *   - 1 horizontal stroke at row 2 across planks 1-2
 *   - 1 vertical stroke for plank 4's top meter
 *
 * Example 2: n=2, a=[2,2] → 2
 *
 * Constraints: 1 ≤ n ≤ 10^5, 1 ≤ a[i] ≤ 10^9
 *
 * ========================== SOLUTION APPROACH =============================
 *
 * Divide and Conquer (recursion on ranges):
 *
 * For a range [l, r] with an already-painted base height `base`:
 *
 *   Option A (all vertical): Use (r - l + 1) vertical strokes.
 *
 *   Option B (horizontal first): Find the minimum height `minH` in [l, r].
 *     - Paint (minH - base) horizontal strokes to fill up to minH.
 *     - This splits the range into sub-problems at positions where a[i] == minH.
 *     - Recursively solve each contiguous segment above minH.
 *
 *   Answer = min(Option A, Option B).
 *
 * Why it works:
 *   At any level, horizontal strokes efficiently cover wide flat regions,
 *   while vertical strokes are better for tall narrow columns. The recursion
 *   always picks the cheaper option.
 *
 * Complexity: O(n log n) average, O(n^2) worst case.
 *   (Can be optimized to O(n log n) with segment trees for range-min queries,
 *    but the simple recursion passes for n ≤ 10^5.)
 *
 * =========================================================================
 */

import java.util.Scanner;

public class PaintingFence {

    public static long minStrokes(int[] a, int l, int r, int base) {
        if (l > r) return 0;

        // Option A: paint each plank vertically
        long vertical = r - l + 1;

        // Find minimum height in [l, r]
        int minH = a[l];
        for (int i = l + 1; i <= r; i++) {
            minH = Math.min(minH, a[i]);
        }

        // Option B: horizontal strokes from base up to minH, then recurse on segments above minH
        long horizontal = minH - base;
        int i = l;
        while (i <= r) {
            if (a[i] > minH) {
                int j = i;
                while (j <= r && a[j] > minH) j++;
                horizontal += minStrokes(a, i, j - 1, minH);
                i = j;
            } else {
                i++;
            }
        }

        return Math.min(vertical, horizontal);
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = sc.nextInt();

        System.out.println(minStrokes(a, 0, n - 1, 0));
    }
}
