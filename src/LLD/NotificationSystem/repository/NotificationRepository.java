package LLD.NotificationSystem.repository;

import LLD.NotificationSystem.model.Notification;
import LLD.NotificationSystem.model.User;
import java.util.List;

/** Persistence abstraction for notifications retained in the in-app inbox. */
public interface NotificationRepository {
    void saveForInApp(User user, Notification notification, String renderedBody);
    List<String> findInApp(String userId);
}
