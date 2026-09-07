package LLD.ThreadPool;

/** Unit of work accepted by the custom thread pool. */
@FunctionalInterface
public interface Job {
    void execute();
}
