package LLD.ThreadPool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Demonstrates bounded parallelism with four workers and ten jobs. */
public final class ThreadPoolDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPool pool = new ThreadPool(4);
        AtomicInteger runningJobs = new AtomicInteger();
        AtomicInteger maximumConcurrentJobs = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(10);

        for (int jobId = 1; jobId <= 10; jobId++) {
            int currentJobId = jobId;
            pool.submit(() -> {
                int running = runningJobs.incrementAndGet();
                maximumConcurrentJobs.updateAndGet(previous ->
                        Math.max(previous, running));
                try {
                    System.out.println("Starting job " + currentJobId
                            + " on " + Thread.currentThread().getName());
                    Thread.sleep(100);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    runningJobs.decrementAndGet();
                    completed.countDown();
                }
            });
        }

        completed.await();
        pool.shutdown();
        pool.awaitTermination();

        System.out.println("Maximum concurrent jobs: " + maximumConcurrentJobs.get());
        System.out.println("Pool terminated: " + pool.isTerminated());
    }

    private ThreadPoolDemo() {
    }
}
