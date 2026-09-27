package LLD.SnakeGame;

import java.util.*;

/*
Problem statement:
Design a Snake Game that provides the following functionalities:

An arena of N x N.
The snake does not move automatically; movement is based on user input.
The snake can move in all four directions.
Random food spawns; later, food might change to power food.
When the snake eats food, its length increases, and a new food spawns.
Game ends if the snake touches the wall or its own body.
If a new food spawns, it should be placed at a valid coordinate, avoiding the snake’s body.
 */
public class SnakeGame {
    private static final int INITIAL_SNAKE_SIZE = 3;
    private static final int POWER_FOOD_SPAWN_PERCENT = 20;
    private static final Set<String> VALID_DIRECTIONS = new HashSet<>(Arrays.asList("U", "D", "L", "R"));

    private final Snake snake;
    private final Board board;
    private final Random random = new Random();
    private final boolean growEveryFiveMoves;
    private Food food;
    private int score = 0;
    private int moveCount = 0;
    private boolean gameOver = false;
    private String lastDirection = "R"; // Default movement

    public SnakeGame(int n) {
        this(n, n);
    }

    public SnakeGame(int height, int width) {
        this(height, width, false);
    }

    public SnakeGame(int height, int width, boolean growEveryFiveMoves) {
        if (height < 3 || width < 3) {
            throw new IllegalArgumentException("Board must be at least 3x3");
        }
        this.growEveryFiveMoves = growEveryFiveMoves;
        board = new Board(height, width);
        snake = new Snake(new Point(height / 2, INITIAL_SNAKE_SIZE - 1), INITIAL_SNAKE_SIZE);
        dropFood();
    }

    private void dropFood() {
        Set<Point> occupied = snake.getBodySet();
        if (occupied.size() == board.getArea()) {
            gameOver = true;
            System.out.println("🎉 You win! Snake filled the board.");
            return;
        }

        while (true) {
            Point p = board.getRandomPoint();
            if (!occupied.contains(p)) {
                food = new Food(p, getRandomFoodType());
                break;
            }
        }
    }

    private FoodType getRandomFoodType() {
        return random.nextInt(100) < POWER_FOOD_SPAWN_PERCENT ? FoodType.POWER : FoodType.NORMAL;
    }

    public int moveSnake(String direction) {
        if (gameOver) {
            return -1;
        }

        if (direction == null || !VALID_DIRECTIONS.contains(direction)) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }

        // Prevent reversing into itself
        if ((lastDirection.equals("U") && direction.equals("D")) || (lastDirection.equals("D") && direction.equals("U")) || (lastDirection.equals("L") && direction.equals("R")) || (lastDirection.equals("R") && direction.equals("L"))) {
            direction = lastDirection; // Ignore invalid opposite direction
        }
        lastDirection = direction;

        Point currentHead = snake.getHead();
        Point newHead = currentHead.move(direction);
        moveCount++;
        boolean ateFood = newHead.equals(food.getPosition());
        boolean grow = ateFood || shouldGrowByMoveCount();

        // Wall collision
        if (!board.isInside(newHead)) {
            gameOver = true;
            System.out.println("💥 Snake hit the wall!");
            return -1;
        }

        // Self collision
        if (snake.isCollision(newHead, grow)) {
            gameOver = true;
            System.out.println("💥 Snake bit itself!");
            return -1;
        }

        snake.move(newHead, grow);

        if (ateFood) {
            score += food.getType().getScore();
            dropFood();
        }

        return score;
    }

    private boolean shouldGrowByMoveCount() {
        return growEveryFiveMoves && moveCount % 5 == 0;
    }

    public Point getFood() {
        return food.getPosition();
    }

    public FoodType getFoodType() {
        return food.getType();
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getScore() {
        return score;
    }
}
