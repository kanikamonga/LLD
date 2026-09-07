package LLD.NotificationSystem.repository;

import LLD.NotificationSystem.model.Notification;
import LLD.NotificationSystem.model.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe in-memory repository for the in-app inbox. */
public final class InMemoryNotificationRepository implements NotificationRepository {
    private final Map<String, List<String>> notifications = new HashMap<>();

    @Override
    public synchronized void saveForInApp(User user, Notification notification,
                                           String renderedBody) {
        notifications.computeIfAbsent(user.id(), ignored -> new ArrayList<>())
                .add(notification.title() + "|" + renderedBody);
    }

    @Override
    public synchronized List<String> findInApp(String userId) {
        return Collections.unmodifiableList(new ArrayList<>(
                notifications.getOrDefault(userId, Collections.emptyList())));
    }
}
