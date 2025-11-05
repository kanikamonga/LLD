package DSA;

import java.util.*;

public class PopularContent {
    private Map<Integer, Integer> popularityMap; // contentId -> popularity
    private PriorityQueue<int[]> maxHeap; // {popularity, contentId}

    public PopularContent() {
        popularityMap = new HashMap<>();
        maxHeap = new PriorityQueue<>((a, b) -> {
            if (b[0] == a[0]) return a[1] - b[1]; // tie: smaller contentId first
            return b[0] - a[0]; // higher popularity first
        });
    }

    public void increasePopularity(int contentId) {
        int newPopularity = popularityMap.getOrDefault(contentId, 0) + 1;
        popularityMap.put(contentId, newPopularity);
        maxHeap.offer(new int[]{newPopularity, contentId});
    }

    public void decreasePopularity(int contentId) {
        if (!popularityMap.containsKey(contentId)) return;
        int newPopularity = popularityMap.get(contentId) - 1;
        if (newPopularity <= 0) {
            popularityMap.remove(contentId);
        } else {
            popularityMap.put(contentId, newPopularity);
            maxHeap.offer(new int[]{newPopularity, contentId});
        }
    }

    public int getMostPopular() {
        while (!maxHeap.isEmpty()) {
            int[] top = maxHeap.peek();
            int actualPopularity = popularityMap.getOrDefault(top[1], 0);
            if (top[0] != actualPopularity) {
                maxHeap.poll(); // stale entry
            } else {
                return top[1];
            }
        }
        return -1; // no content with popularity > 0
    }

    // Demo
    public static void main(String[] args) {
        PopularContent tracker = new PopularContent();
        tracker.increasePopularity(101);
        tracker.increasePopularity(102);
        tracker.increasePopularity(101);
        System.out.println(tracker.getMostPopular()); // 101 (popularity 2)
        tracker.decreasePopularity(101);
        System.out.println(tracker.getMostPopular()); // 102 (popularity 1, tie broken by smaller ID)
        tracker.decreasePopularity(101);
        tracker.decreasePopularity(102);
        System.out.println(tracker.getMostPopular()); // -1 (all popularity <= 0)
    }
}
