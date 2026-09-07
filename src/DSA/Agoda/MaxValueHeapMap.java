package DSA.Agoda;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

/*
 * Heap variant:
 *
 * - A HashMap<Integer, Integer> stores the current value for every key.
 * - A max heap stores every inserted key-value entry.
 * - Updates and deletes do not search the heap. Old entries become stale and
 *   are removed lazily when they reach the top during getMax().
 *
 * An old entry is valid when the map still contains the same value for its key.
 * If an update assigns the same value again, the duplicate heap entry is
 * harmless because both entries represent the same current maximum value.
 *
 * Complexity:
 * insert: O(log U), where U is the number of heap entries
 * delete: O(1) average
 * getMax: O(1) when the top is current, O(log U) per stale entry removed;
 *         amortized O(log U), since every stale entry is removed only once
 * space: O(U), including stale entries awaiting lazy removal
 */
public class MaxValueHeapMap {

    private static class HeapEntry {
        private final int key;
        private final int value;

        private HeapEntry(int key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    private final Map<Integer, Integer> activeValues =
            new HashMap<Integer, Integer>();

    private final PriorityQueue<HeapEntry> maxHeap =
            new PriorityQueue<HeapEntry>(new Comparator<HeapEntry>() {
                @Override
                public int compare(HeapEntry first, HeapEntry second) {
                    return Integer.compare(second.value, first.value);
                }
            });

    public void insert(int key, int value) {
        activeValues.put(key, value);
        maxHeap.offer(new HeapEntry(key, value));
    }

    /**
     * Removes the active mapping in O(1). Its heap entry is discarded later.
     */
    public boolean delete(int key) {
        return activeValues.remove(key) != null;
    }

    public int getMax() {
        removeStaleEntries();
        if (maxHeap.isEmpty()) {
            throw new NoSuchElementException("No values are present");
        }
        return maxHeap.peek().value;
    }

    public int size() {
        return activeValues.size();
    }

    private void removeStaleEntries() {
        while (!maxHeap.isEmpty()) {
            HeapEntry candidate = maxHeap.peek();
            Integer activeValue = activeValues.get(candidate.key);

            // An entry is stale after deletion or after the key is updated to
            // a different value.
            if (activeValue != null && activeValue == candidate.value) {
                return;
            }
            maxHeap.poll();
        }
    }

    public static void main(String[] args) {
        MaxValueHeapMap values = new MaxValueHeapMap();

        values.insert(1, 10);
        values.insert(2, 30);
        values.insert(3, 20);
        System.out.println(values.getMax()); // 30

        values.insert(2, 5);
        System.out.println(values.getMax()); // 20

        values.delete(3);
        System.out.println(values.getMax()); // 10

        // Repeated values are safe even without version tracking.
        values.insert(1, 20);
        values.insert(1, 10);
        System.out.println(values.getMax()); // 10
    }
}
