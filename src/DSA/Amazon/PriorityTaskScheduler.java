package DSA.Amazon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/*
 * Problem:
 * Given tasks with an id, priority, and processing time, produce an execution
 * order that:
 *
 * 1. Always executes a higher-priority task before a lower-priority task.
 * 2. Minimizes total waiting time among tasks with the same priority.
 * 3. Supports changing a pending task's priority efficiently.
 *
 * Interpretation:
 * A larger priority value means higher priority. Strict priority means tasks
 * from different priority levels cannot be reordered for optimization.
 * Within one priority level, Shortest Processing Time (SPT) first minimizes
 * the sum of waiting times.
 *
 * Data structures:
 * - TreeMap<Integer, TreeSet<Task>> stores priority levels in sorted order.
 * - TreeSet<Task> stores one priority's tasks by processing time, then id.
 * - tasksById supports O(1) lookup during priority updates.
 *
 * Complexity:
 * addTask:       O(log N)
 * updatePriority: O(log N)
 * nextTask:      O(log N)
 * executionOrder: O(N log N)
 * space:         O(N)
 */
public class PriorityTaskScheduler {

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

        @Override
        public String toString() {
            return "Task{id=" + id + ", priority=" + priority
                    + ", time=" + processingTime + "}";
        }
    }

    private final Map<Integer, Task> tasksById =
            new java.util.HashMap<Integer, Task>();

    private final TreeMap<Integer, TreeSet<Task>> tasksByPriority =
            new TreeMap<Integer, TreeSet<Task>>();

    private final Comparator<Task> shortestFirst =
            new Comparator<Task>() {
                @Override
                public int compare(Task first, Task second) {
                    int byTime = Integer.compare(
                            first.processingTime, second.processingTime);
                    return byTime != 0
                            ? byTime
                            : Integer.compare(first.id, second.id);
                }
            };

    public void addTask(Task task) {
        if (task == null || tasksById.containsKey(task.id)) {
            throw new IllegalArgumentException("Task is null or id already exists");
        }

        tasksById.put(task.id, task);
        getPrioritySet(task.priority).add(task);
    }

    public void updatePriority(int taskId, int newPriority) {
        Task task = tasksById.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Unknown task id: " + taskId);
        }

        TreeSet<Task> oldSet = tasksByPriority.get(task.priority);
        oldSet.remove(task);
        if (oldSet.isEmpty()) {
            tasksByPriority.remove(task.priority);
        }

        task.priority = newPriority;
        getPrioritySet(newPriority).add(task);
    }

    /**
     * Removes and returns the next task, or null when no tasks remain.
     */
    public Task nextTask() {
        if (tasksByPriority.isEmpty()) {
            return null;
        }

        // lastKey() gives the greatest priority because larger is higher.
        int highestPriority = tasksByPriority.lastKey();
        TreeSet<Task> candidates = tasksByPriority.get(highestPriority);
        Task next = candidates.pollFirst();

        if (candidates.isEmpty()) {
            tasksByPriority.remove(highestPriority);
        }
        tasksById.remove(next.id);
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

    private TreeSet<Task> getPrioritySet(int priority) {
        TreeSet<Task> tasks = tasksByPriority.get(priority);
        if (tasks == null) {
            tasks = new TreeSet<Task>(shortestFirst);
            tasksByPriority.put(priority, tasks);
        }
        return tasks;
    }

    public static void main(String[] args) {
        PriorityTaskScheduler scheduler = new PriorityTaskScheduler();
        scheduler.addTask(new Task(1, 10, 5));
        scheduler.addTask(new Task(2, 20, 2));

        System.out.println(scheduler.executionOrder()); // [2, 1]

        PriorityTaskScheduler dynamicScheduler = new PriorityTaskScheduler();
        dynamicScheduler.addTask(new Task(1, 10, 5));
        dynamicScheduler.addTask(new Task(2, 10, 2));
        dynamicScheduler.addTask(new Task(3, 5, 1));
        dynamicScheduler.updatePriority(3, 20);

        System.out.println(dynamicScheduler.executionOrder()); // [3, 2, 1]
    }
}
