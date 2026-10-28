package DSA.Apple;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

public class GoBoardCapture {
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /*
     * BFS each distinct white group adjacent to the move.
     * If a group has no empty neighbor (liberty), remove and count it.
     *
     * Time: O(m * n) in the worst case, where m and n are board dimensions.
     * Space: O(m * n) for visited cells and the BFS queue.
     */
    public int captureWhiteStones(char[][] board, int row, int col) {
        int rows = board.length;
        int cols = board[0].length;
        boolean[][] visited = new boolean[rows][cols];

        board[row][col] = 'b';
        int captured = 0;

        for (int[] direction : DIRECTIONS) {
            int nextRow = row + direction[0];
            int nextCol = col + direction[1];
            if (inside(nextRow, nextCol, rows, cols)
                    && board[nextRow][nextCol] == 'w'
                    && !visited[nextRow][nextCol]) {
                captured += captureGroup(board, nextRow, nextCol, visited, rows, cols);
            }
        }

        return captured;
    }

    private int captureGroup(
            char[][] board, int startRow, int startCol, boolean[][] visited, int rows, int cols) {
        Queue<int[]> queue = new ArrayDeque<>();
        ArrayList<int[]> group = new ArrayList<>();
        queue.offer(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;
        boolean hasLiberty = false;

        while (!queue.isEmpty()) {
            int[] position = queue.poll();
            int currentRow = position[0];
            int currentCol = position[1];
            group.add(position);

            for (int[] direction : DIRECTIONS) {
                int nextRow = currentRow + direction[0];
                int nextCol = currentCol + direction[1];
                if (!inside(nextRow, nextCol, rows, cols)) {
                    continue;
                }

                if (board[nextRow][nextCol] == 'e') {
                    hasLiberty = true;
                } else if (board[nextRow][nextCol] == 'w' && !visited[nextRow][nextCol]) {
                    visited[nextRow][nextCol] = true;
                    queue.offer(new int[]{nextRow, nextCol});
                }
            }
        }

        if (hasLiberty) {
            return 0;
        }
        return group.size();
    }

    private boolean inside(int row, int col, int rows, int cols) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public static void main(String[] args) {
        char[][][] boards = {
                {
                        "eeeebbb".toCharArray(),
                        "eeeebwb".toCharArray(),
                        "eeeebeb".toCharArray(),
                        "eeeeeee".toCharArray()
                },
                {
                        "eeeebbb".toCharArray(),
                        "eeebwwb".toCharArray(),
                        "eeeebeb".toCharArray(),
                        "eeeeeee".toCharArray()
                },
                {
                        "eeeebbb".toCharArray(),
                        "eeeewwb".toCharArray(),
                        "eeeebeb".toCharArray(),
                        "eeeeeee".toCharArray()
                },
                {
                        "eebbbbb".toCharArray(),
                        "eebwewb".toCharArray(),
                        "eebbbeb".toCharArray(),
                        "eeeeeee".toCharArray()
                }
        };
        int[] expected = {1, 2, 0, 0};
        GoBoardCapture solver = new GoBoardCapture();

        for (int i = 0; i < boards.length; i++) {
            int actual = solver.captureWhiteStones(boards[i], 2, 5);
            System.out.println("Example " + (i + 1)
                    + ": expected=" + expected[i] + ", actual=" + actual);
            if (actual != expected[i]) {
                throw new AssertionError("Example " + (i + 1) + " failed");
            }
        }
    }
}
