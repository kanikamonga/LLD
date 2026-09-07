package LLD.multithreading.ThreadPool;

/** Indicates that a job was submitted after shutdown began. */
public final class RejectedJobException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RejectedJobException(String message) {
        super(message);
    }
}
