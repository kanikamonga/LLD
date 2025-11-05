package LLD.SnakeGame;

import java.util.Random;

public class Board {
    int width, height;
    Random rand = new Random();

    public Board(int height, int width) {
        this.height = height;
        this.width = width;
    }

    public boolean isInside(Point p) {
        return p.x >= 0 && p.x < height && p.y >= 0 && p.y < width;
    }

    public Point getRandomPoint() {
        return new Point(rand.nextInt(height), rand.nextInt(width));
    }
}
