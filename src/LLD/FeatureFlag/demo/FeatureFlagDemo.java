package LLD.FeatureFlag.demo;

import LLD.FeatureFlag.exception.ChannelHasChildrenException;
import LLD.FeatureFlag.service.FeatureFlagService;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Demonstrates direct values, DFS inheritance, caching, updates, and deletion rules. */
public final class FeatureFlagDemo {
    public static void main(String[] args) {
        FeatureFlagService service = new FeatureFlagService();
        service.createChannel("global", flags("darkMode", true), Collections.emptyList());
        service.createChannel("india", flags("darkMode", false), Collections.singletonList("global"));
        service.createChannel("mobile", Collections.emptyMap(), Collections.singletonList("india"));
        service.createChannel("beta", flags("newCheckout", true),
                Arrays.asList("mobile", "global"));

        System.out.println("india.darkMode = " + service.getFeature("india", "darkMode"));
        System.out.println("mobile.darkMode = " + service.getFeature("mobile", "darkMode"));
        System.out.println("beta.newCheckout = " + service.getFeature("beta", "newCheckout"));
        System.out.println("beta.unknown = " + service.getFeature("beta", "unknown"));

        service.setFeature("global", "darkMode", false);
        System.out.println("mobile.darkMode after global update = "
                + service.getFeature("mobile", "darkMode"));

        try {
            service.deleteChannel("global");
        } catch (ChannelHasChildrenException exception) {
            System.out.println(exception.getMessage());
        }
        service.deleteChannel("beta");
        System.out.println("Channels after deleting leaf beta: " + service.channelIds());
    }

    private static Map<String, Boolean> flags(String name, boolean value) {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put(name, value);
        return flags;
    }

    private FeatureFlagDemo() {
    }
}
