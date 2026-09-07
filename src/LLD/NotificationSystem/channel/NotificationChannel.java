package LLD.NotificationSystem.channel;

import LLD.NotificationSystem.model.ChannelType;
import LLD.NotificationSystem.model.User;
/** Port implemented by each external or in-app delivery mechanism. */
public interface NotificationChannel {
    ChannelType type();
    void send(User user, String title, String body);
}
