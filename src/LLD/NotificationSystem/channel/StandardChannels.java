package LLD.NotificationSystem.channel;

import LLD.NotificationSystem.model.ChannelType;
import LLD.NotificationSystem.model.User;
/** Shared adapter base for email, SMS, and push providers. */
abstract class StandardChannel implements NotificationChannel {
    private final ChannelType type;

    StandardChannel(ChannelType type) {
        this.type = type;
    }

    @Override
    public final ChannelType type() {
        return type;
    }

    @Override
    public void send(User user, String title, String body) {
        if (user.endpointFor(type) == null) {
            throw new ChannelSendException("No endpoint configured for " + type);
        }
        deliver(user.endpointFor(type), title, body);
    }

    protected abstract void deliver(String endpoint, String title, String body);
}

/** Adapter for an email provider. */
final class EmailChannel extends StandardChannel {
    EmailChannel() {
        super(ChannelType.EMAIL);
    }

    @Override
    protected void deliver(String endpoint, String title, String body) {
        // Integrate with an email provider here.
    }
}

/** Adapter for an SMS provider. */
final class SmsChannel extends StandardChannel {
    SmsChannel() {
        super(ChannelType.SMS);
    }

    @Override
    protected void deliver(String endpoint, String title, String body) {
        // Integrate with an SMS provider here.
    }
}

/** Adapter for a push provider. */
final class PushChannel extends StandardChannel {
    PushChannel() {
        super(ChannelType.PUSH);
    }

    @Override
    protected void deliver(String endpoint, String title, String body) {
        // Integrate with a push provider here.
    }
}
