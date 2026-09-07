package DSA.Amazon;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/*
 * Problem:
 * Given a reward grid and custom movement rules, find the maximum total reward
 * on a path from a start cell to an end cell.
 *
 * A movement rule is represented by an offset:
 *   {rowDelta, columnDelta}
 *
 * For example:
 *   {0, 1}  means move right
 *   {1, 0}  means move down
 *   {1, 1}  means move diagonally down-right
 *
 * The transition graph must be a DAG. This is required for dynamic programming
 * because a cycle could allow a path to revisit cells indefinitely.
 *
 * Approach:
 * 1. Treat every grid cell as a graph vertex.
 * 2. Add a directed edge for every valid custom movement.
 * 3. Use Kahn's algorithm to obtain a topological order.
 * 4. dp[cell] stores the maximum reward for reaching that cell.
 * 5. Relax each outgoing transition in topological order.
 *
 * Negative rewards are supported because unreachable states use Long.MIN_VALUE
 * rather than zero.
 *
 * Time Complexity: O(R * C * M), where M is the number of movement rules.
 * Space Complexity: O(R * C).
 *
 * Note:
 * With only right/down moves, the sample grid has maximum path value 16
 * (1 + 4 + 5 + 6). Therefore, the stated output 12 requires an additional
 * restriction not included in the prompt.
 */
public class MaximumRewardGridPath {

    private static final long NEGATIVE_INFINITY = Long.MIN_VALUE / 4;

    public static long maxReward(
            int[][] grid,
            int[][] moves,
            int startRow,
            int startColumn,
            int endRow,
            int endColumn) {
        validateInput(grid, moves, startRow, startColumn, endRow, endColumn);

        int rows = grid.length;
        int columns = grid[0].length;
        int totalCells = rows * columns;
        int[] indegree = new int[totalCells];

        // Count incoming edges so cells can be topologically ordered.
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                for (int[] move : moves) {
                    int nextRow = row + move[0];
                    int nextColumn = column + move[1];
                    if (isInside(nextRow, nextColumn, rows, columns)) {
                        indegree[index(nextRow, nextColumn, columns)]++;
                    }
                }
            }
        }

        Deque<Integer> zeroIndegree = new ArrayDeque<Integer>();
        for (int cell = 0; cell < totalCells; cell++) {
            if (indegree[cell] == 0) {
                zeroIndegree.offer(cell);
            }
        }

        int[] topologicalOrder = new int[totalCells];
        int orderSize = 0;
        while (!zeroIndegree.isEmpty()) {
            int cell = zeroIndegree.poll();
            topologicalOrder[orderSize++] = cell;

            int row = cell / columns;
            int column = cell % columns;
            for (int[] move : moves) {
                int nextRow = row + move[0];
                int nextColumn = column + move[1];
                if (!isInside(nextRow, nextColumn, rows, columns)) {
                    continue;
                }

                int nextCell = index(nextRow, nextColumn, columns);
                if (--indegree[nextCell] == 0) {
                    zeroIndegree.offer(nextCell);
                }
            }
        }

        if (orderSize != totalCells) {
            throw new IllegalArgumentException("Movement rules contain a cycle");
        }

        long[] best = new long[totalCells];
        Arrays.fill(best, NEGATIVE_INFINITY);
        int start = index(startRow, startColumn, columns);
        int end = index(endRow, endColumn, columns);
        best[start] = grid[startRow][startColumn];

        for (int position = 0; position < orderSize; position++) {
            int cell = topologicalOrder[position];
            if (best[cell] == NEGATIVE_INFINITY) {
                continue;
            }

            int row = cell / columns;
            int column = cell % columns;
            for (int[] move : moves) {
                int nextRow = row + move[0];
                int nextColumn = column + move[1];
                if (!isInside(nextRow, nextColumn, rows, columns)) {
                    continue;
                }

                int nextCell = index(nextRow, nextColumn, columns);
                long candidate = best[cell] + grid[nextRow][nextColumn];
                best[nextCell] = Math.max(best[nextCell], candidate);
            }
        }

        if (best[end] == NEGATIVE_INFINITY) {
            throw new IllegalArgumentException("Destination is unreachable");
        }
        return best[end];
    }

    private static void validateInput(
            int[][] grid,
            int[][] moves,
            int startRow,
            int startColumn,
            int endRow,
            int endColumn) {
        if (grid == null || grid.length == 0 || grid[0] == null
                || grid[0].length == 0 || moves == null || moves.length == 0) {
            throw new IllegalArgumentException("Grid and moves are required");
        }

        int columns = grid[0].length;
        for (int[] row : grid) {
            if (row == null || row.length != columns) {
                throw new IllegalArgumentException("Grid must be rectangular");
            }
        }
        for (int[] move : moves) {
            if (move == null || move.length != 2
                    || (move[0] == 0 && move[1] == 0)) {
                throw new IllegalArgumentException("Invalid movement rule");
            }
        }

        if (!isInside(startRow, startColumn, grid.length, columns)
                || !isInside(endRow, endColumn, grid.length, columns)) {
            throw new IllegalArgumentException("Start or destination is outside grid");
        }
    }

    private static int index(int row, int column, int columns) {
        return row * columns + column;
    }

    private static boolean isInside(
            int row, int column, int rows, int columns) {
        return row >= 0 && row < rows && column >= 0 && column < columns;
    }

    public static void main(String[] args) {
        int[][] grid = {
                {1, 2, 3},
                {4, 5, 6}
        };
        int[][] standardMoves = {
                {0, 1}, // right
                {1, 0}  // down
        };

        System.out.println(maxReward(grid, standardMoves, 0, 0, 1, 2));
        // 16: 1 -> 4 -> 5 -> 6
    }
}
