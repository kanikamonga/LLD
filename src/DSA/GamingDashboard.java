package DSA;

import java.util.*;

/**
 * In a gaming platform dashboard for metric value avg jf
 * top 100 scores - average of bottom 100 scores for every new score return updated metric.
 * for size<100 , It should be part of both heaps and return 0
 */
public class GamingDashboard {
    private PriorityQueue<Integer> topHeap;     // min-heap for top 100
    private PriorityQueue<Integer> bottomHeap;  // max-heap for bottom 100
    private long sumTop = 0;
    private long sumBottom = 0;
    private int totalScores = 0;

    public GamingDashboard() {
        topHeap = new PriorityQueue<>(); // natural order (smallest on top)
        bottomHeap = new PriorityQueue<>(Collections.reverseOrder()); // max-heap
    }

    public double addScore(int score) {
        totalScores++;

        // If we have < 100 scores total -> treat as part of both heaps
        if (totalScores <= 3) {
            topHeap.offer(score);
            bottomHeap.offer(score);
            sumTop += score;
            sumBottom += score;
            return 0.0; // metric = 0 until we reach 100 scores
        }

        // Otherwise maintain top 100 and bottom 100
        // --- Handle topHeap (keep largest 100) ---
        if (topHeap.size() < 3) {
            topHeap.offer(score);
            sumTop += score;
        } else if (score > topHeap.peek()) {
            int removed = topHeap.poll();
            sumTop -= removed;
            topHeap.offer(score);
            sumTop += score;
        }

        // --- Handle bottomHeap (keep smallest 100) ---
        if (bottomHeap.size() < 3) {
            bottomHeap.offer(score);
            sumBottom += score;
        } else if (score < bottomHeap.peek()) {
            int removed = bottomHeap.poll();
            sumBottom -= removed;
            bottomHeap.offer(score);
            sumBottom += score;
        }

        // Now compute metric
        double avgTop = (double) sumTop / topHeap.size();
        double avgBottom = (double) sumBottom / bottomHeap.size();

        return avgTop - avgBottom;
    }

    public static void main(String[] args) {
        GamingDashboard dashboard = new GamingDashboard();

        int[] scores = {10, 30, 40, 20};

        for (int score : scores) {
            double metric = dashboard.addScore(score);
            System.out.println("After score " + score + " => Metric: " + metric);
        }
    }
}
