package LLD.NotificationSystem.channel;

import LLD.NotificationSystem.model.ChannelType;
import LLD.NotificationSystem.model.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** In-memory channel useful for demonstrations and unit tests. */
public final class RecordingChannel implements NotificationChannel {
    private final ChannelType type;
    private final List<String> deliveries = new ArrayList<>();

    public RecordingChannel(ChannelType type) {
        if (type == null) throw new IllegalArgumentException("Channel type is required");
        this.type = type;
    }

    @Override
    public ChannelType type() { return type; }

    @Override
    public synchronized void send(User user, String title, String body) {
        if (user.endpointFor(type) == null) {
            throw new ChannelSendException("No endpoint configured for " + type);
        }
        deliveries.add(user.id() + "|" + title + "|" + body);
    }

    public synchronized List<String> deliveries() {
        return Collections.unmodifiableList(new ArrayList<>(deliveries));
    }
}
