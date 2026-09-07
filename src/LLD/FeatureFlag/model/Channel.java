package LLD.FeatureFlag.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A channel owns explicit flags and an ordered list of parent channels. */
public final class Channel {
    private final String id;
    private final Map<String, Boolean> features;
    private final List<String> parentIds;

    public Channel(String id, Map<String, Boolean> features, List<String> parentIds) {
        this.id = id;
        this.features = new LinkedHashMap<>(features);
        this.parentIds = Collections.unmodifiableList(new java.util.ArrayList<>(parentIds));
    }

    public String id() {
        return id;
    }

    public Map<String, Boolean> features() {
        return Collections.unmodifiableMap(features);
    }

    public List<String> parentIds() {
        return parentIds;
    }

    public void setFeature(String featureName, boolean value) {
        features.put(featureName, value);
    }

    public void removeParent(String parentId) {
        parentIds.remove(parentId);
    }
}
