package LLD.FeatureFlag.exception;

/** Raised when an operation references an unknown channel. */
public final class ChannelNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ChannelNotFoundException(String message) {
        super(message);
    }
}
