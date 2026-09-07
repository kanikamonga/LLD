package LLD.NotificationSystem.service;

import LLD.NotificationSystem.channel.NotificationChannel;
import LLD.NotificationSystem.model.ChannelType;
import LLD.NotificationSystem.model.Notification;
import LLD.NotificationSystem.model.User;
import LLD.NotificationSystem.preference.DeliveryFrequency;
import LLD.NotificationSystem.preference.UserPreference;
import LLD.NotificationSystem.repository.NotificationRepository;
import java.util.EnumMap;
import java.util.Map;

/** Applies preferences and routes personalized notifications to channels. */
public final class NotificationService {
    private final Map<ChannelType, NotificationChannel> channels =
            new EnumMap<>(ChannelType.class);
    private final NotificationRepository repository;
    private final Map<String, UserPreference> preferences = new java.util.HashMap<>();

    public NotificationService(NotificationRepository repository) {
        if (repository == null) throw new IllegalArgumentException("Repository is required");
        this.repository = repository;
    }

    public void registerChannel(NotificationChannel channel) {
        if (channel == null) throw new IllegalArgumentException("Channel is required");
        channels.put(channel.type(), channel);
    }

    public synchronized void setPreference(User user, UserPreference preference) {
        preferences.put(user.id(), preference);
    }

    public void publish(Notification notification, User user) {
        UserPreference preference;
        synchronized (this) {
            preference = preferences.get(user.id());
        }
        if (preference == null || preference.isGloballyOptedOut()
                || !preference.isSubscribed(notification.type())
                || preference.frequencyFor(notification.type()) != DeliveryFrequency.IMMEDIATE) {
            return;
        }

        String body = personalize(notification.bodyTemplate(), user, notification.data());
        for (ChannelType type : preference.channelsFor(notification.type())) {
            if (type == ChannelType.IN_APP) {
                repository.saveForInApp(user, notification, body);
            } else {
                NotificationChannel channel = channels.get(type);
                if (channel == null) {
                    throw new IllegalStateException("No channel registered for " + type);
                }
                channel.send(user, notification.title(), body);
            }
        }
    }

    private String personalize(String template, User user, Map<String, String> data) {
        String result = template.replace("{userName}", user.name());
        for (Map.Entry<String, String> entry : data.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
