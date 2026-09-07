package LLD.FeatureFlag.exception;

/** Raised for missing parents, self-parenting, or cyclic inheritance. */
public final class InvalidChannelHierarchyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidChannelHierarchyException(String message) {
        super(message);
    }
}
