/**
 * SudokuValidator - Validate a 9x9 Sudoku Board
 *
 * Problem: Determine if a 9x9 Sudoku board is valid.
 * Only the filled cells need to be validated according to:
 * 1. Each row must contain digits 1-9 without repetition
 * 2. Each column must contain digits 1-9 without repetition
 * 3. Each 3x3 sub-grid must contain digits 1-9 without repetition
 *
 * Input: 9x9 2D array of characters ('1'-'9' or '.')
 * Output: boolean - true if valid, false otherwise
 *
 * Time Complexity: O(1) - exactly 81 cells to check
 * Space Complexity: O(1) - fixed size data structures (9x9)
 */
public class SudokuValidator {

    /**
     * Validate a 9x9 Sudoku board
     * @param board 9x9 2D char array where '.' represents empty cells
     * @return true if board is valid, false otherwise
     */
    public static boolean isValidSudoku(char[][] board) {
        // Arrays to track seen numbers in rows, columns, and 3x3 boxes
        boolean[][] rows = new boolean[9][10];      // rows[i][digit]
        boolean[][] cols = new boolean[9][10];      // cols[j][digit]
        boolean[][] boxes = new boolean[9][10];     // boxes[boxIndex][digit]

        // Validate each cell
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                char cell = board[i][j];

                // Skip empty cells
                if (cell == '.') {
                    continue;
                }

                // Convert char to digit (1-9)
                int digit = cell - '0';

                // Calculate 3x3 box index (0-8)
                int boxIndex = (i / 3) * 3 + (j / 3);

                // Check if digit already exists in row, column, or box
                if (rows[i][digit] || cols[j][digit] || boxes[boxIndex][digit]) {
                    return false;
                }

                // Mark digit as seen
                rows[i][digit] = true;
                cols[j][digit] = true;
                boxes[boxIndex][digit] = true;
            }
        }

        return true;
    }

    /**
     * Alternative solution using HashSet approach
     * Time: O(81) = O(1)
     * Space: O(9*3) = O(1)
     */
    public static boolean isValidSudokuSet(char[][] board) {
        java.util.Set<String> seen = new java.util.HashSet<>();

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                char cell = board[i][j];

                if (cell == '.') {
                    continue;
                }

                // Create unique strings for row, column, and box
                String rowKey = "row" + i + cell;
                String colKey = "col" + j + cell;
                String boxKey = "box" + (i / 3) + (j / 3) + cell;

                // If any key already exists, it's a duplicate
                if (!seen.add(rowKey) || !seen.add(colKey) || !seen.add(boxKey)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Example usage and test cases
     */
    public static void main(String[] args) {
        // Test 1: Valid Sudoku
        char[][] board1 = {
            {'5', '3', '.', '.', '7', '.', '.', '.', '.'},
            {'6', '.', '.', '1', '9', '5', '.', '.', '.'},
            {'.', '9', '8', '.', '.', '.', '.', '6', '.'},
            {'8', '.', '.', '.', '6', '.', '.', '.', '3'},
            {'4', '.', '.', '8', '.', '3', '.', '.', '1'},
            {'7', '.', '.', '.', '2', '.', '.', '.', '6'},
            {'.', '6', '.', '.', '.', '.', '2', '8', '.'},
            {'.', '.', '.', '4', '1', '9', '.', '.', '5'},
            {'.', '.', '.', '.', '8', '.', '.', '7', '9'}
        };

        System.out.println("Test 1 (Valid board): " + isValidSudoku(board1));  // true

        // Test 2: Invalid - duplicate in row
        char[][] board2 = {
            {'5', '3', '5', '.', '7', '.', '.', '.', '.'},  // 5 appears twice
            {'6', '.', '.', '1', '9', '5', '.', '.', '.'},
            {'.', '9', '8', '.', '.', '.', '.', '6', '.'},
            {'8', '.', '.', '.', '6', '.', '.', '.', '3'},
            {'4', '.', '.', '8', '.', '3', '.', '.', '1'},
            {'7', '.', '.', '.', '2', '.', '.', '.', '6'},
            {'.', '6', '.', '.', '.', '.', '2', '8', '.'},
            {'.', '.', '.', '4', '1', '9', '.', '.', '5'},
            {'.', '.', '.', '.', '8', '.', '.', '7', '9'}
        };

        System.out.println("Test 2 (Duplicate in row): " + isValidSudoku(board2));  // false

        // Test 3: Invalid - duplicate in column
        char[][] board3 = {
            {'5', '3', '.', '.', '7', '.', '.', '.', '.'},
            {'6', '.', '.', '1', '9', '5', '.', '.', '.'},
            {'.', '9', '8', '.', '.', '.', '.', '6', '.'},
            {'8', '.', '.', '.', '6', '.', '.', '.', '3'},
            {'4', '.', '.', '8', '.', '3', '.', '.', '1'},
            {'7', '.', '.', '.', '2', '.', '.', '.', '6'},
            {'.', '6', '.', '.', '.', '.', '2', '8', '.'},
            {'.', '.', '.', '4', '1', '9', '.', '.', '5'},
            {'5', '.', '.', '.', '8', '.', '.', '7', '9'}  // 5 appears in column again
        };

        System.out.println("Test 3 (Duplicate in column): " + isValidSudoku(board3));  // false

        // Test 4: Invalid - duplicate in 3x3 box
        char[][] board4 = {
            {'5', '3', '.', '.', '7', '.', '.', '.', '.'},
            {'6', '.', '.', '1', '9', '5', '.', '.', '.'},
            {'.', '9', '8', '.', '.', '.', '.', '6', '.'},
            {'8', '.', '.', '.', '6', '.', '.', '.', '3'},
            {'4', '.', '.', '8', '.', '3', '.', '.', '1'},
            {'7', '.', '.', '.', '2', '.', '.', '.', '6'},
            {'.', '6', '.', '.', '.', '.', '2', '8', '.'},
            {'.', '.', '.', '4', '1', '9', '.', '.', '5'},
            {'.', '.', '8', '.', '8', '.', '.', '7', '9'}  // 8 appears in same 3x3 box
        };

        System.out.println("Test 4 (Duplicate in 3x3 box): " + isValidSudoku(board4));  // false

        // Test 5: All empty board
        char[][] board5 = {
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'},
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'},
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'},
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'},
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'},
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'},
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'},
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'},
            {'.', '.', '.', '.', '.', '.', '.', '.', '.'}
        };

        System.out.println("Test 5 (Empty board): " + isValidSudoku(board5));  // true

        // Compare both approaches
        System.out.println("\nComparing approaches:");
        System.out.println("Array approach: " + isValidSudoku(board1));
        System.out.println("HashSet approach: " + isValidSudokuSet(board1));
    }
}
