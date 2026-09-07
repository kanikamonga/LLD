package DSA.Amazon;

import java.util.Arrays;

/*
 * Problem:
 * Select exactly one value from every row of a ragged grid. If column j is
 * selected in the current row, the next row may use only columns j - 1, j,
 * or j + 1, provided those columns exist.
 *
 * DP definition:
 * dp[row][column] = maximum sum for a valid path ending at this cell.
 *
 * For every value at column j in the current row, the possible previous
 * columns are j - 1, j, and j + 1. Take the best of those three states and
 * add the current value.
 *
 * The arrays can have different lengths, so only valid indices in the
 * previous row are considered.
 *
 * Time Complexity: O(total number of cells)
 * Space Complexity: O(total number of cells)
 */
public class MaximumRaggedGridPath {

    private static final long NEGATIVE_INFINITY = Long.MIN_VALUE / 4;

    public static long maximumSum(int[][] grid) {
        if (grid == null || grid.length == 0) {
            throw new IllegalArgumentException("Grid cannot be empty");
        }

        long[][] dp = new long[grid.length][];
        for (int row = 0; row < grid.length; row++) {
            dp[row] = new long[grid[row].length];
            Arrays.fill(dp[row], NEGATIVE_INFINITY);
        }

        // Any cell in the first row can be selected as the starting point.
        for (int column = 0; column < grid[0].length; column++) {
            dp[0][column] = grid[0][column];
        }

        for (int i = 1; i < grid.length; i++) {
            for (int j = 0; j < grid[i].length; j++) {
                long bestPrevious = NEGATIVE_INFINITY;

                // Previous j - 1.
                if (j - 1 >= 0 && j - 1 < grid[i - 1].length) {
                    bestPrevious = Math.max(bestPrevious, dp[i - 1][j - 1]);
                }

                // Previous  j.
                if (j < grid[i - 1].length) {
                    bestPrevious = Math.max(bestPrevious, dp[i - 1][j]);
                }

                // Previous  j + 1.
                if (j + 1 < grid[i - 1].length) {
                    bestPrevious = Math.max(bestPrevious, dp[i - 1][j + 1]);
                }

                if (bestPrevious != NEGATIVE_INFINITY) {
                    dp[i][j] = bestPrevious + grid[i][j];
                }
            }
        }

        long answer = NEGATIVE_INFINITY;
        for (long sum : dp[grid.length - 1]) {
            answer = Math.max(answer, sum);
        }
        return answer;
    }

    public static void main(String[] args) {
        int[][] grid = {
                {3, 1, 4},
                {2, 8, 5, 9},
                {7, 6, 6}
        };

        System.out.println(maximumSum(grid)); // 19
    }
}
