package LLD.NotificationSystem.preference;

import LLD.NotificationSystem.model.ChannelType;
import LLD.NotificationSystem.model.NotificationType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Encapsulates subscriptions, selected channels, frequency, and opt-out state. */
public final class UserPreference {
    private boolean globallyOptedOut;
    private final Map<NotificationType, Set<ChannelType>> channels =
            new EnumMap<>(NotificationType.class);
    private final Map<NotificationType, DeliveryFrequency> frequencies =
            new EnumMap<>(NotificationType.class);

    public void optOut() { globallyOptedOut = true; }
    public void optIn() { globallyOptedOut = false; }
    public boolean isGloballyOptedOut() { return globallyOptedOut; }

    public void subscribe(NotificationType type, Set<ChannelType> selectedChannels) {
        if (type == null || selectedChannels == null || selectedChannels.isEmpty()) {
            throw new IllegalArgumentException("Notification type and channels are required");
        }
        channels.put(type, EnumSet.copyOf(selectedChannels));
        frequencies.putIfAbsent(type, DeliveryFrequency.IMMEDIATE);
    }

    public void unsubscribe(NotificationType type) {
        channels.remove(type);
        frequencies.remove(type);
    }

    public void setFrequency(NotificationType type, DeliveryFrequency frequency) {
        if (type == null || frequency == null) {
            throw new IllegalArgumentException("Type and frequency are required");
        }
        frequencies.put(type, frequency);
    }

    public boolean isSubscribed(NotificationType type) {
        return channels.containsKey(type);
    }

    public Set<ChannelType> channelsFor(NotificationType type) {
        Set<ChannelType> selected = channels.get(type);
        return selected == null ? Collections.emptySet() : EnumSet.copyOf(selected);
    }

    public DeliveryFrequency frequencyFor(NotificationType type) {
        return frequencies.getOrDefault(type, DeliveryFrequency.IMMEDIATE);
    }
}
