package DSA.Google.Multithreading.PipelineManager;

/*
 * ========================== PROBLEM DESCRIPTION ==========================
 *
 * Design and implement a Pipeline Manager capable of handling job execution
 * with dependencies.
 *
 * Job Structure:
 *   Each job has a jobId and a doWork() method that throws JobException on failure.
 *
 * Dependency Management:
 *   Jobs can depend on one or more preceding jobs.
 *   Example graph:
 *       A --> B --> C ------\
 *       |                    --> G
 *       ---> E --> F -------/
 *       K --> L --> M
 *
 * Execution Requirements:
 *   - Process jobs in dependency order.
 *   - A job starts only after ALL its dependencies have succeeded.
 *   - Run independent jobs in parallel to minimize idle time.
 *
 * Termination Conditions:
 *   - Success: all jobs executed successfully.
 *   - Failure: if any job fails, terminate immediately with no job left running.
 *
 * ========================== SOLUTION APPROACH =============================
 *
 * 1. GRAPH MODELING
 *    - Store the DAG as two adjacency lists:
 *      dependencies (jobId -> predecessors) and dependents (jobId -> successors).
 *    - Track in-degree (number of unfinished predecessors) for each job.
 *
 * 2. CYCLE DETECTION
 *    - Before execution, run Kahn's algorithm (BFS topological sort).
 *    - If processed count != total jobs, a cycle exists → throw JobException.
 *
 * 3. PARALLEL EXECUTION (BFS-style with thread pool)
 *    - Use a fixed-size ExecutorService (threads = available processors).
 *    - Initially submit all root jobs (in-degree == 0) to the pool.
 *    - When a job completes, atomically decrement in-degree of its successors
 *      (via ConcurrentHashMap.compute). If a successor's in-degree hits 0,
 *      submit it to the pool → this gives maximum parallelism.
 *    - A CountDownLatch (size = total jobs) blocks the main thread until all
 *      jobs finish or are cancelled.
 *
 * 4. FAILURE HANDLING
 *    - An AtomicBoolean `failed` flag is set on the first failure.
 *    - The failing job does a BFS over its entire downstream subgraph,
 *      calling latch.countDown() for each, so the main thread unblocks.
 *    - Any subsequently submitted job checks the flag and skips execution.
 *    - After latch.await(), executor.shutdownNow() ensures no thread remains.
 *
 * Complexity:
 *    - Time:  O(V + E) for graph setup + cycle detection; O(V) job submissions.
 *    - Space: O(V + E) for adjacency lists and in-degree map.
 *
 * =========================================================================
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PipelineManager {
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Map<String, List<String>> dependencies = new HashMap<>(); // jobId -> predecessors
    private final Map<String, List<String>> dependents = new HashMap<>();   // jobId -> successors

    public void addJob(Job job) {
        jobs.put(job.getJobId(), job);
        dependencies.putIfAbsent(job.getJobId(), new ArrayList<>());
        dependents.putIfAbsent(job.getJobId(), new ArrayList<>());
    }

    /**
     * Declares that `jobId` depends on `dependsOnJobId`.
     * dependsOnJobId must complete before jobId can start.
     */
    public void addDependency(String jobId, String dependsOnJobId) {
        if (!jobs.containsKey(jobId) || !jobs.containsKey(dependsOnJobId)) {
            throw new IllegalArgumentException("Both jobs must be added before declaring a dependency");
        }
        dependencies.get(jobId).add(dependsOnJobId);
        dependents.get(dependsOnJobId).add(jobId);
    }

    /**
     * Executes all jobs respecting dependency order, running independent jobs in parallel.
     * On any job failure, the pipeline terminates immediately with no job left running.
     */
    public void execute() throws JobException, InterruptedException {
        validateNoCycles();

        int threadCount = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicBoolean failed = new AtomicBoolean(false);

        // Track remaining in-degree (number of unfinished predecessors) for each job
        Map<String, Integer> inDegree = new ConcurrentHashMap<>();
        for (String jobId : jobs.keySet()) {
            inDegree.put(jobId, dependencies.get(jobId).size());
        }

        CountDownLatch latch = new CountDownLatch(jobs.size());
        ConcurrentLinkedQueue<JobException> errors = new ConcurrentLinkedQueue<>();

        // Kick off all root jobs (in-degree == 0)
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                submitJob(entry.getKey(), executor, inDegree, latch, failed, errors);
            }
        }

        latch.await();
        executor.shutdownNow();

        if (!errors.isEmpty()) {
            throw errors.peek();
        }
    }

    private void submitJob(String jobId, ExecutorService executor,
                           Map<String, Integer> inDegree,
                           CountDownLatch latch,
                           AtomicBoolean failed,
                           ConcurrentLinkedQueue<JobException> errors) {
        executor.submit(() -> {
            // If pipeline already failed, skip this job and release its downstream
            if (failed.get()) {
                cancelDownstream(jobId, latch);
                return;
            }

            try {
                Job job = jobs.get(jobId);
                System.out.println("[" + Thread.currentThread().getName() + "] Starting  " + job);
                job.doWork();
                System.out.println("[" + Thread.currentThread().getName() + "] Completed " + job);
            } catch (JobException e) {
                System.err.println("[" + Thread.currentThread().getName() + "] FAILED    " + jobId + ": " + e.getMessage());
                errors.add(e);
                failed.set(true);
                cancelDownstream(jobId, latch);
                return;
            }

            latch.countDown();

            // Unblock dependent jobs: decrement their in-degree, submit when ready
            for (String successorId : dependents.get(jobId)) {
                int remaining = inDegree.compute(successorId, (k, v) -> v - 1);
                if (remaining == 0) {
                    submitJob(successorId, executor, inDegree, latch, failed, errors);
                }
            }
        });
    }

    /**
     * On failure, count down the latch for this job and all transitively dependent jobs
     * so the main thread's await() unblocks.
     */
    private void cancelDownstream(String startJobId, CountDownLatch latch) {
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add(startJobId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) continue;
            latch.countDown();
            for (String successor : dependents.getOrDefault(current, Collections.emptyList())) {
                queue.add(successor);
            }
        }
    }

    /** Kahn's algorithm to detect cycles in the dependency graph. */
    private void validateNoCycles() throws JobException {
        Map<String, Integer> deg = new HashMap<>();
        for (String jobId : jobs.keySet()) {
            deg.put(jobId, dependencies.get(jobId).size());
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : deg.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            processed++;
            for (String successor : dependents.get(curr)) {
                int val = deg.get(successor) - 1;
                deg.put(successor, val);
                if (val == 0) queue.add(successor);
            }
        }

        if (processed != jobs.size()) {
            throw new JobException("Cycle detected in job dependency graph");
        }
    }
}
