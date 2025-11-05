package LLD.SnakeGame;

import java.util.*;

public class Snake {
    Deque<Point> body;
    Set<Point> bodySet;

    public Snake(Point start, int initialSize) {
        body = new LinkedList<>();
        bodySet = new HashSet<>();

        // Initialize snake horizontally to the left
        for (int i = 0; i < initialSize; i++) {
            Point p = new Point(start.x, start.y - i);
            body.addLast(p);
            bodySet.add(p);
        }
    }

    public Point getHead() {
        return body.peekFirst();
    }

    public boolean isCollision(Point newHead) {
        return bodySet.contains(newHead);
    }

    public void move(Point newHead, boolean grow) {
        body.addFirst(newHead);
        bodySet.add(newHead);

        if (!grow) {
            Point tail = body.removeLast();
            bodySet.remove(tail);
        }
    }

    public Set<Point> getBodySet() {
        return new HashSet<>(bodySet);
    }

    public int getLength() {
        return body.size();
    }
}
