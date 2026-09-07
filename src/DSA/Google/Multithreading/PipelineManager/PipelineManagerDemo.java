package DSA.Google.Multithreading.PipelineManager;

/**
 * Demo for PipelineManager with the example dependency graph:
 *
 *  A --> B --> C ------\
 *  |                    --> G
 *  ---> E --> F -------/
 *  K --> L --> M
 */
public class PipelineManagerDemo {

    static Job createJob(String id, long workMs) {
        return new Job(id) {
            @Override
            public void doWork() throws JobException {
                try {
                    Thread.sleep(workMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new JobException("Job " + id + " interrupted");
                }
            }
        };
    }

    static Job createFailingJob(String id, long workMs) {
        return new Job(id) {
            @Override
            public void doWork() throws JobException {
                try {
                    Thread.sleep(workMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new JobException("Job " + id + " failed!");
            }
        };
    }

    public static void main(String[] args) {
        System.out.println("=== Scenario 1: All jobs succeed ===\n");
        runSuccessScenario();

        System.out.println("\n=== Scenario 2: Job E fails — pipeline terminates early ===\n");
        runFailureScenario();
    }

    private static void runSuccessScenario() {
        PipelineManager pm = new PipelineManager();

        pm.addJob(createJob("A", 100));
        pm.addJob(createJob("B", 200));
        pm.addJob(createJob("C", 150));
        pm.addJob(createJob("E", 300));
        pm.addJob(createJob("F", 100));
        pm.addJob(createJob("G", 200));
        pm.addJob(createJob("K", 100));
        pm.addJob(createJob("L", 150));
        pm.addJob(createJob("M", 100));

        // A --> B --> C --> G
        pm.addDependency("B", "A");
        pm.addDependency("C", "B");
        pm.addDependency("G", "C");
        // A --> E --> F --> G
        pm.addDependency("E", "A");
        pm.addDependency("F", "E");
        pm.addDependency("G", "F");
        // K --> L --> M (independent chain)
        pm.addDependency("L", "K");
        pm.addDependency("M", "L");

        try {
            pm.execute();
            System.out.println("\nPipeline completed successfully!");
        } catch (JobException e) {
            System.err.println("Pipeline failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Pipeline interrupted");
        }
    }

    private static void runFailureScenario() {
        PipelineManager pm = new PipelineManager();

        pm.addJob(createJob("A", 100));
        pm.addJob(createJob("B", 200));
        pm.addJob(createJob("C", 150));
        pm.addJob(createFailingJob("E", 50)); // E will fail fast
        pm.addJob(createJob("F", 100));
        pm.addJob(createJob("G", 200));
        pm.addJob(createJob("K", 100));
        pm.addJob(createJob("L", 150));
        pm.addJob(createJob("M", 100));

        pm.addDependency("B", "A");
        pm.addDependency("C", "B");
        pm.addDependency("G", "C");
        pm.addDependency("E", "A");
        pm.addDependency("F", "E");
        pm.addDependency("G", "F");
        pm.addDependency("L", "K");
        pm.addDependency("M", "L");

        try {
            pm.execute();
            System.out.println("\nPipeline completed successfully!");
        } catch (JobException e) {
            System.err.println("\nPipeline failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Pipeline interrupted");
        }
    }
}
