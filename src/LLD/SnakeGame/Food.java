package LLD.SnakeGame;

public class Food {
    private final Point position;
    private final FoodType type;

    public Food(Point position, FoodType type) {
        this.position = position;
        this.type = type;
    }

    public Point getPosition() {
        return position;
    }

    public FoodType getType() {
        return type;
    }
}
