package LLD.SnakeGame;

import java.util.*;

public class FoodManager {
    Queue<Point> foodQueue;

    public FoodManager(List<Point> foodList) {
        foodQueue = new LinkedList<>(foodList);
    }

    public Point getNextFood() {
        return foodQueue.peek();
    }

    public void removeFood(Point p) {
        foodQueue.poll();
    }
}
