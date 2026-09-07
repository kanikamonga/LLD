package LLD.NotificationSystem.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable notification content and personalization data. */
public final class Notification {
    private final String id;
    private final NotificationType type;
    private final String title;
    private final String bodyTemplate;
    private final Map<String, String> data;

    public Notification(String id, NotificationType type, String title,
                        String bodyTemplate, Map<String, String> data) {
        this.id = required(id, "notification id");
        this.type = type == null ? throwMissing("notification type") : type;
        this.title = required(title, "notification title");
        this.bodyTemplate = required(bodyTemplate, "notification body");
        this.data = data == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(data));
    }

    public String id() { return id; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String bodyTemplate() { return bodyTemplate; }
    public Map<String, String> data() { return data; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    private static <T> T throwMissing(String field) {
        throw new IllegalArgumentException(field + " is required");
    }
}
