package LLD.SnakeGame;

import java.util.*;

public class SnakeGame {
    private Snake snake;
    private Board board;
    private Point food;
    private int score = 0;
	int moveCount = 0;
    private boolean gameOver = false;
    private String lastDirection = "R"; // Default movement

    public SnakeGame(int height, int width) {
        if (height < 3 || width < 3) {
            throw new IllegalArgumentException("Board must be at least 3x3");
        }
        board = new Board(height, width);
        snake = new Snake(new Point(height / 2, width / 2), 3);
        dropFood();
    }

    private void dropFood() {
        if (snake.getLength() == board.height * board.width) {
            // Snake fills the board → win
            gameOver = true;
            System.out.println("🎉 You win! Snake filled the board.");
            return;
        }
        Random rand = new Random();
        while (true) {
            Point p = board.getRandomPoint();
            if (!snake.getBodySet().contains(p)) {
                food = p;
                break;
            }
        }
    }

    public int moveSnake(String direction) {
	    if (gameOver) {
		    return -1;
	    }

        if (direction == null || !Arrays.asList("U", "D", "L", "R").contains(direction)) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }

        // Prevent reversing into itself
        if ((lastDirection.equals("U") && direction.equals("D")) ||
            (lastDirection.equals("D") && direction.equals("U")) ||
            (lastDirection.equals("L") && direction.equals("R")) ||
            (lastDirection.equals("R") && direction.equals("L"))) {
            direction = lastDirection; // Ignore invalid opposite direction
        }
        lastDirection = direction;

        Point currentHead = snake.getHead();
        Point newHead = currentHead.move(direction);
		moveCount++;
	    boolean grow = (moveCount % 5 == 0);

        // Wall collision
        if (!board.isInside(newHead)) {
            gameOver = true;
            System.out.println("💥 Snake hit the wall!");
            return -1;
        }

        // Self collision
        if (snake.isCollision(newHead)) {
            gameOver = true;
            System.out.println("💥 Snake bit itself!");
            return -1;
        }

        if (newHead.equals(food)) {
            grow = true;
            score++;
            dropFood();
        }

        snake.move(newHead, grow);
        return score;
    }

    public Point getFood() {
        return food;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getScore() {
        return score;
    }
}
