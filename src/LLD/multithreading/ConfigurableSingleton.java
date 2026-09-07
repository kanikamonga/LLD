package LLD.multithreading;


import java.util.*;
import java.util.concurrent.*;

/**
 * Thread-safe Singleton configured via Builder. Includes a multithreading demo in main().
 */
public final class ConfigurableSingleton {
    private static volatile ConfigurableSingleton instance;

    private final String host;
    private final int port;
    private final boolean featureEnabled;
    private final long timeoutMs;

    private ConfigurableSingleton(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.featureEnabled = b.featureEnabled;
        this.timeoutMs = b.timeoutMs;
    }

    /**
     * Returns the singleton instance if initialized; otherwise throws.
     */
    public static ConfigurableSingleton getInstance() {
        ConfigurableSingleton s = instance;
        if (s == null) {
            throw new IllegalStateException("Singleton not initialized. Call Builder.build() first.");
        }
        return s;
    }

    /**
     * Internal initializer invoked by Builder.build().
     * Uses double-checked locking and volatile instance for safe publication.
     */
    private static ConfigurableSingleton init(Builder b) {
        ConfigurableSingleton s = instance;
        if (s == null) {
            synchronized (ConfigurableSingleton.class) {
                s = instance;
                if (s == null) {
                    instance = new ConfigurableSingleton(b);
                }
            }
        }
        return instance;
    }

    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isFeatureEnabled() { return featureEnabled; }
    public long getTimeoutMs() { return timeoutMs; }

    // Fluent Builder
    public static final class Builder {
        private String host = "localhost";
        private int port = 80;
        private boolean featureEnabled = false;
        private long timeoutMs = 1000;

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }
        public Builder port(int port) {
            if (port <= 0 || port > 65535) throw new IllegalArgumentException("Invalid port");
            this.port = port;
            return this;
        }
        public Builder featureEnabled(boolean v) {
            this.featureEnabled = v;
            return this;
        }
        public Builder timeoutMs(long ms) {
            if (ms < 0) throw new IllegalArgumentException("timeoutMs must be >= 0");
            this.timeoutMs = ms;
            return this;
        }

        /**
         * Initializes (if necessary) and returns the singleton instance.
         * If another thread has already initialized the singleton, this call
         * returns that instance (the current build's values are ignored).
         */
        public ConfigurableSingleton build() {
            return ConfigurableSingleton.init(this);
        }
    }

    // Demonstration of concurrent initialization
    public static void main(String[] args) throws Exception {
        final int THREADS = 8;
        ExecutorService ex = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ConfigurableSingleton>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            final int idx = i;
            futures.add(ex.submit(() -> {
                ready.countDown();
                start.await(); // wait for synchronous start
                // Each thread tries to initialize with different params
                Builder builder = new Builder()
                        .host("host-" + idx)
                        .port(8000 + idx)
                        .featureEnabled(idx % 2 == 0)
                        .timeoutMs(1000 + idx * 100L);
                ConfigurableSingleton s = builder.build();
                System.out.printf("Thread-%d: instanceId=%d host=%s port=%d%n",
                        idx, System.identityHashCode(s), s.getHost(), s.getPort());
                return s;
            }));
        }

        // Wait until all threads are ready, then start them simultaneously
        ready.await();
        start.countDown();

        // Collect results and verify single instance
        Set<Integer> ids = new HashSet<>();
        Set<String> hosts = new HashSet<>();
        for (Future<ConfigurableSingleton> f : futures) {
            ConfigurableSingleton s = f.get();
            ids.add(System.identityHashCode(s));
            hosts.add(s.getHost());
        }

        System.out.println("Unique instance count: " + ids.size());
        System.out.println("Observed host(s): " + hosts);
        ex.shutdown();
    }
}
