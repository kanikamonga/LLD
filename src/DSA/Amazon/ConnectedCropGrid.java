package DSA.Amazon;

import java.util.Arrays;

/*
 * Problem:
 * Fill an M x N grid using crop identifiers 0, 1, 2, ..., where cropArray[i]
 * is the required number of cells for crop i. Cells of the same crop must be
 * connected horizontally or vertically.
 *
 * Approach:
 * Fill the grid in row-major order. A crop occupies consecutive positions in
 * this order. Consecutive row-major cells are always adjacent:
 *
 * - Usually they are side-by-side in the same row.
 * - At the end of a row, the next cell is directly below the first cell of
 *   the next row.
 *
 * Therefore, every crop's cells form one connected component.
 *
 * Time Complexity: O(M * N)
 * Space Complexity: O(M * N) for the returned grid.
 */
public class ConnectedCropGrid {

    public static int[][] fillGrid(int[] cropArray, int rows, int columns) {
        if (cropArray == null || rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("Invalid crop counts or dimensions");
        }

        long requiredCells = 0;
        for (int frequency : cropArray) {
            requiredCells += frequency;
        }

        if (requiredCells != (long) rows * columns) {
            throw new IllegalArgumentException(
                    "Crop frequencies must equal rows * columns");
        }

        int[][] grid = new int[rows][columns];
        int row = 0;
        int column = 0;

        for (int crop = 0; crop < cropArray.length; crop++) {
            for (int count = 0; count < cropArray[crop]; count++) {
                grid[row][column] = crop;
                column++;
                if (column == columns) {
                    column = 0;
                    row++;
                }
            }
        }

        return grid;
    }

    public static void main(String[] args) {
        int[] cropArray = {3, 2, 1};
        int[][] grid = fillGrid(cropArray, 2, 3);

        for (int[] row : grid) {
            System.out.println(Arrays.toString(row));
        }
    }
}
