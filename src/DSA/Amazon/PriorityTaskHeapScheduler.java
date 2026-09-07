package DSA.Amazon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/*
 * Heap-based priority task scheduler.
 *
 * Heap order:
 * 1. Higher priority first.
 * 2. Shorter processing time first for equal priorities.
 * 3. Smaller task id first for deterministic ties.
 *
 * A Java PriorityQueue has no efficient decrease-key operation. Therefore,
 * updatePriority removes the task from the heap, changes its priority, and
 * inserts it again.
 *
 * Complexity:
 * addTask:        O(log N)
 * updatePriority: O(N) for remove + O(log N) for reinsert
 * nextTask:       O(log N)
 * executionOrder: O(N log N)
 * space:          O(N)
 */
public class PriorityTaskHeapScheduler {

    public static class Task {
        public final int id;
        public int priority;
        public final int processingTime;

        public Task(int id, int priority, int processingTime) {
            if (processingTime < 0) {
                throw new IllegalArgumentException("Processing time cannot be negative");
            }
            this.id = id;
            this.priority = priority;
            this.processingTime = processingTime;
        }
    }

    private final Map<Integer, Task> tasksById = new HashMap<Integer, Task>();

    private final PriorityQueue<Task> heap = new PriorityQueue<Task>(
            Comparator.comparingInt((Task task) -> task.priority)
                    .reversed()
                    .thenComparingInt(task -> task.processingTime)
                    .thenComparingInt(task -> task.id));

    public void addTask(Task task) {
        if (task == null || tasksById.containsKey(task.id)) {
            throw new IllegalArgumentException("Task is null or id already exists");
        }

        tasksById.put(task.id, task);
        heap.offer(task);
    }

    public void updatePriority(int taskId, int newPriority) {
        Task task = tasksById.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Unknown task id: " + taskId);
        }

        // PriorityQueue does not reorder after a field changes, so remove the
        // task before changing it and then insert it at its correct position.
        heap.remove(task);
        task.priority = newPriority;
        heap.offer(task);
    }

    /**
     * Removes and returns the next task, or null if no tasks remain.
     */
    public Task nextTask() {
        Task next = heap.poll();
        if (next != null) {
            tasksById.remove(next.id);
        }
        return next;
    }

    public List<Integer> executionOrder() {
        List<Integer> order = new ArrayList<Integer>(tasksById.size());
        Task task;
        while ((task = nextTask()) != null) {
            order.add(task.id);
        }
        return order;
    }

    public static void main(String[] args) {
        PriorityTaskHeapScheduler scheduler = new PriorityTaskHeapScheduler();
        scheduler.addTask(new Task(1, 10, 5));
        scheduler.addTask(new Task(2, 20, 2));
        System.out.println(scheduler.executionOrder()); // [2, 1]

        PriorityTaskHeapScheduler dynamicScheduler =
                new PriorityTaskHeapScheduler();
        dynamicScheduler.addTask(new Task(1, 10, 5));
        dynamicScheduler.addTask(new Task(2, 10, 2));
        dynamicScheduler.addTask(new Task(3, 5, 1));
        dynamicScheduler.updatePriority(3, 20);
        System.out.println(dynamicScheduler.executionOrder()); // [3, 2, 1]
    }
}
