package LLD.SnakeGame;

public enum FoodType {
    NORMAL(1),
    POWER(2);

    private final int score;

    FoodType(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }
}
