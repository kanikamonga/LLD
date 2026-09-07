package DSA.Google.Multithreading.PipelineManager;

public abstract class Job {
    private final String jobId;

    public Job(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public abstract void doWork() throws JobException;

    @Override
    public String toString() {
        return "Job(" + jobId + ")";
    }
}
