package LLD.FeatureFlag.exception;

/** Raised when a channel ID is reused. */
public final class ChannelAlreadyExistsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ChannelAlreadyExistsException(String message) {
        super(message);
    }
}
