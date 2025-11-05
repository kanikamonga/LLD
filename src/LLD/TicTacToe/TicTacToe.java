package LLD.TicTacToe;

import java.util.Scanner;

public class TicTacToe {
    private char[][] board; // 3x3 game board
    private int
        currentPlayer; // 1 for Player 1, 2 for Player 2

    public TicTacToe()
    {
        board = new char[3][3];
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                board[i][j] = ' ';
            }
        }
        currentPlayer = 1;
    }

    public void printBoard()
    {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                System.out.print(board[i][j]);
                if (j < 2) {
                    System.out.print(" | ");
                }
            }
            System.out.println();
            if (i < 2) {
                System.out.println("---------");
            }
        }
    }

    public boolean isBoardFull()
    {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                if (board[i][j] == ' ') {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean makeMove(int row, int column)
    {
        if (row < 0 || row >= 3 || column < 0 || column >= 3
            || board[row][column] != ' ') {
            return false; // Invalid move
        }
        board[row][column]
            = (currentPlayer == 1) ? 'X' : 'O';
        currentPlayer
            = 3 - currentPlayer; // Switch player (1 to 2 or
                                 // 2 to 1)
        return true;
    }

    public boolean checkWinner()
    {
        // Check rows, columns, and diagonals for a win
        for (int i = 0; i < 3; ++i) {
            if (board[i][0] != ' '
                && board[i][0] == board[i][1]
                && board[i][1] == board[i][2]) {
                return true; // Row win
            }
            if (board[0][i] != ' '
                && board[0][i] == board[1][i]
                && board[1][i] == board[2][i]) {
                return true; // Column win
            }
        }
        if (board[0][0] != ' ' && board[0][0] == board[1][1]
            && board[1][1] == board[2][2]) {
            return true; // Diagonal win
        }
        if (board[0][2] != ' ' && board[0][2] == board[1][1]
            && board[1][1] == board[2][0]) {
            return true; // Diagonal win
        }
        return false;
    }

    public static void main(String[] args)
    {
        TicTacToe game = new TicTacToe();
        Scanner scanner = new Scanner(System.in);
        int row, column;

        while (!game.isBoardFull() && !game.checkWinner()) {
            game.printBoard();

            System.out.print(
                "Player " + game.currentPlayer
                + ", enter your move (row and column): ");
            row = scanner.nextInt();
            column = scanner.nextInt();

            if (game.makeMove(row, column)) {
                System.out.println("Move successful!");
            }
            else {
                System.out.println(
                    "Invalid move. Try again.");
            }
        }

        game.printBoard();

        if (game.checkWinner()) {
            System.out.println("Player "
                               + (3 - game.currentPlayer)
                               + " wins!");
        }
        else {
            System.out.println("It's a draw!");
        }

        scanner.close();
    }
}