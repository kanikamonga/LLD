package LLD.SnakeGame;

public class Main {
    public static void main(String[] args) {
        try {
            SnakeGame game = new SnakeGame(10, 10);

            System.out.println("Initial Food at: " + game.getFood().x + "," + game.getFood().y);

            System.out.println("Score: " + game.moveSnake("R"));
            System.out.println("Score: " + game.moveSnake("L")); // ignored, keeps moving right
            System.out.println("Score: " + game.moveSnake("D"));
            System.out.println("Score: " + game.moveSnake("BAD")); // throws exception
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
        }
    }
}
