package LLD.FeatureFlag.exception;

/** Raised when deleting a channel would leave child channels dangling. */
public final class ChannelHasChildrenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ChannelHasChildrenException(String message) {
        super(message);
    }
}
