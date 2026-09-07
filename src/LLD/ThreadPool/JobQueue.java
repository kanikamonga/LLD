package LLD.ThreadPool;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Unbounded producer-consumer queue.
 *
 * <p>Workers wait using wait() when no work exists. Shutdown wakes all
 * workers; they drain accepted jobs before returning null.</p>
 */
final class JobQueue {
    private final Deque<Job> jobs = new ArrayDeque<>();
    private boolean shutdown;

    synchronized void add(Job job) {
        if (shutdown) {
            throw new RejectedJobException("The job queue is shut down");
        }
        jobs.addLast(job);
        notify();
    }

    synchronized Job take() throws InterruptedException {
        while (jobs.isEmpty() && !shutdown) {
            wait();
        }
        if (!jobs.isEmpty()) {
            return jobs.removeFirst();
        }
        return null;
    }

    synchronized void shutdown() {
        shutdown = true;
        notifyAll();
    }
}
