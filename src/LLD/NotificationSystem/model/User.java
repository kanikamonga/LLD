package LLD.NotificationSystem.model;

import LLD.NotificationSystem.model.ChannelType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Stores a user's identity and configured delivery endpoints. */
public final class User {
    private final String id;
    private final String name;
    private final Map<ChannelType, String> endpoints =
            new EnumMap<>(ChannelType.class);

    public User(String id, String name) {
        this.id = required(id, "user id");
        this.name = required(name, "user name");
    }

    public String id() { return id; }
    public String name() { return name; }

    public void setEndpoint(ChannelType channel, String endpoint) {
        if (channel == null) throw new IllegalArgumentException("Channel is required");
        endpoints.put(channel, required(endpoint, "endpoint"));
    }

    public String endpointFor(ChannelType channel) {
        return endpoints.get(channel);
    }

    public Map<ChannelType, String> endpoints() {
        return Collections.unmodifiableMap(endpoints);
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
