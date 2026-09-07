package LLD.multithreading.ThreadPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size thread pool with graceful shutdown.
 *
 * <p>Submission and shutdown are serialized so a job is either accepted into
 * the queue or rejected; it cannot be lost during the transition.</p>
 */
public final class ThreadPool {
    private final JobQueue queue = new JobQueue();
    private final List<Worker> workers = new ArrayList<>();
    private ThreadPoolState state = ThreadPoolState.RUNNING;

    public ThreadPool(int workerCount) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count must be positive");
        }
        for (int index = 1; index <= workerCount; index++) {
            Worker worker = new Worker("thread-pool-worker-" + index);
            workers.add(worker);
            worker.start();
        }
    }

    /** Adds a job atomically with respect to shutdown. */
    public synchronized void submit(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("Job cannot be null");
        }
        if (state != ThreadPoolState.RUNNING) {
            throw new RejectedJobException("Thread pool is not accepting jobs");
        }
        queue.add(job);
    }

    /**
     * Stops accepting jobs and drains all jobs already accepted.
     */
    public synchronized void shutdown() {
        if (state != ThreadPoolState.RUNNING) {
            return;
        }
        state = ThreadPoolState.SHUTDOWN;
        queue.shutdown();
    }

    /**
     * Waits until all accepted jobs complete and workers exit.
     */
    public void awaitTermination() throws InterruptedException {
        for (Worker worker : workers) {
            worker.join();
        }
        synchronized (this) {
            state = ThreadPoolState.TERMINATED;
        }
    }

    public synchronized boolean isShutdown() {
        return state != ThreadPoolState.RUNNING;
    }

    public synchronized boolean isTerminated() {
        return state == ThreadPoolState.TERMINATED;
    }

    private final class Worker extends Thread {
        private Worker(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Job job = queue.take();
                    if (job == null) {
                        return;
                    }
                    try {
                        job.execute();
                    } catch (RuntimeException exception) {
                        // One faulty job must not terminate its worker.
                        System.err.println(getName() + " job failed: "
                                + exception.getMessage());
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
