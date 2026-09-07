package LLD.FeatureFlag.service;

import LLD.FeatureFlag.exception.ChannelAlreadyExistsException;
import LLD.FeatureFlag.exception.ChannelHasChildrenException;
import LLD.FeatureFlag.exception.ChannelNotFoundException;
import LLD.FeatureFlag.exception.InvalidChannelHierarchyException;
import LLD.FeatureFlag.model.Channel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe in-memory feature flag service.
 *
 * <p>Inheritance uses depth-first search in the parent order supplied to
 * {@link #createChannel(String, Map, List)}. The first explicit value found wins.
 * Missing flags default to false.</p>
 */
public final class FeatureFlagService {
    private final Map<String, Channel> channels = new HashMap<>();
    private final Map<LookupKey, Boolean> lookupCache = new HashMap<>();

    /**
     * Creates a channel and validates that its complete parent graph is acyclic.
     */
    public synchronized void createChannel(String channelId,
                                            Map<String, Boolean> features,
                                            List<String> parents) {
        requireText(channelId, "channel id");
        if (channels.containsKey(channelId)) {
            throw new ChannelAlreadyExistsException("Channel already exists: " + channelId);
        }
        if (features == null || parents == null) {
            throw new IllegalArgumentException("Features and parents are required");
        }
        validateFeatures(features);
        validateParents(channelId, parents);
        channels.put(channelId, new Channel(channelId, features, parents));
        lookupCache.clear();
    }

    /**
     * Returns the first explicitly defined value found by ordered DFS, or false.
     */
    public synchronized boolean getFeature(String channelId, String featureName) {
        requireText(featureName, "feature name");
        requireChannel(channelId);
        LookupKey key = new LookupKey(channelId, featureName);
        Boolean cached = lookupCache.get(key);
        if (cached != null) {
            return cached;
        }

        Boolean resolvedValue = resolve(channelId, featureName, new HashSet<>());
        boolean value = resolvedValue != null && resolvedValue;
        lookupCache.put(key, value);
        return value;
    }

    /** Sets only the explicit value on the requested channel. */
    public synchronized void setFeature(String channelId, String featureName, boolean value) {
        requireText(featureName, "feature name");
        Channel channel = requireChannel(channelId);
        channel.setFeature(featureName, value);
        // A parent update can change any descendant lookup, so invalidate atomically.
        lookupCache.clear();
    }

    /**
     * Deletes a channel only when no other channel references it as a parent.
     * This avoids silently changing inheritance behavior for existing children.
     */
    public synchronized void deleteChannel(String channelId) {
        requireChannel(channelId);
        List<String> children = new ArrayList<>();
        for (Channel channel : channels.values()) {
            if (channel.parentIds().contains(channelId)) {
                children.add(channel.id());
            }
        }
        if (!children.isEmpty()) {
            throw new ChannelHasChildrenException(
                    "Cannot delete channel " + channelId + "; children: " + children);
        }
        channels.remove(channelId);
        lookupCache.clear();
    }

    public synchronized List<String> channelIds() {
        return Collections.unmodifiableList(new ArrayList<>(channels.keySet()));
    }

    private Boolean resolve(String channelId, String featureName, Set<String> visiting) {
        if (!visiting.add(channelId)) {
            throw new InvalidChannelHierarchyException("Cycle detected at channel: " + channelId);
        }
        Channel channel = channels.get(channelId);
        Boolean explicitValue = channel.features().get(featureName);
        if (explicitValue != null) {
            return explicitValue;
        }
        for (String parentId : channel.parentIds()) {
            Boolean inheritedValue = resolve(parentId, featureName, visiting);
            if (inheritedValue != null) {
                visiting.remove(channelId);
                return inheritedValue;
            }
            visiting.remove(parentId);
        }
        visiting.remove(channelId);
        return null;
    }

    private void validateParents(String channelId, List<String> parents) {
        Set<String> uniqueParents = new LinkedHashSet<>(parents);
        if (uniqueParents.size() != parents.size()) {
            throw new InvalidChannelHierarchyException("Duplicate parent in channel: " + channelId);
        }
        for (String parentId : parents) {
            requireText(parentId, "parent channel id");
            if (channelId.equals(parentId) || !channels.containsKey(parentId)) {
                throw new InvalidChannelHierarchyException(
                        "Invalid parent " + parentId + " for channel " + channelId);
            }
            ensureNoPathTo(parentId, channelId, new HashSet<>());
        }
    }

    private void ensureNoPathTo(String current, String target, Set<String> visiting) {
        if (!visiting.add(current)) {
            throw new InvalidChannelHierarchyException("Existing channel hierarchy contains a cycle");
        }
        if (current.equals(target)) {
            throw new InvalidChannelHierarchyException("Adding parent would create a cycle");
        }
        for (String parentId : channels.get(current).parentIds()) {
            ensureNoPathTo(parentId, target, visiting);
        }
    }

    private Channel requireChannel(String channelId) {
        requireText(channelId, "channel id");
        Channel channel = channels.get(channelId);
        if (channel == null) {
            throw new ChannelNotFoundException("Channel not found: " + channelId);
        }
        return channel;
    }

    private static void validateFeatures(Map<String, Boolean> features) {
        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            requireText(entry.getKey(), "feature name");
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("Feature value cannot be null");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }

    private static final class LookupKey {
        private final String channelId;
        private final String featureName;

        private LookupKey(String channelId, String featureName) {
            this.channelId = channelId;
            this.featureName = featureName;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof LookupKey)) return false;
            LookupKey key = (LookupKey) other;
            return channelId.equals(key.channelId) && featureName.equals(key.featureName);
        }

        @Override
        public int hashCode() {
            return 31 * channelId.hashCode() + featureName.hashCode();
        }
    }
}
