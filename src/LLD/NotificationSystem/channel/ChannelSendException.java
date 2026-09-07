package LLD.NotificationSystem.channel;

/** Signals that a channel cannot deliver a notification. */
public final class ChannelSendException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ChannelSendException(String message) {
        super(message);
    }
}
