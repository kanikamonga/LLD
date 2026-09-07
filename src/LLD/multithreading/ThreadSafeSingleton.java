package LLD.multithreading;

import java.util.Objects;

/**
 * Thread-safe Singleton configured via Builder.
 *
 * Concurrency: uses volatile + double-checked locking for lazy initialization and safe publication.
 * Initialization: the Builder.build() method initializes the single instance. Subsequent build() calls
 * return the already-initialized instance (their parameters are ignored).
 *
 * Usage:
 *   // Initialize once (e.g., at startup)
 *   ThreadSafeSingleton s = new ThreadSafeSingleton.Builder()
 *       .host("api.example.com")
 *       .port(443)
 *       .featureEnabled(true)
 *       .build();
 *
 *   // Later
 *   ThreadSafeSingleton same = ThreadSafeSingleton.getInstance();
 */
public final class ThreadSafeSingleton {
    private static volatile ThreadSafeSingleton instance;

    private final String host;
    private final int port;
    private final boolean featureEnabled;
    private final long timeoutMs;

    private ThreadSafeSingleton(Builder b) {
        this.host = b.host;
        this.port = b.port;
        this.featureEnabled = b.featureEnabled;
        this.timeoutMs = b.timeoutMs;
    }

    /**
     * Returns the initialized singleton instance. Throws if not yet initialized.
     */
    public static ThreadSafeSingleton getInstance() {
        ThreadSafeSingleton s = instance;
        if (s == null) {
            throw new IllegalStateException("Singleton not initialized. Call Builder.build() first.");
        }
        return s;
    }

    /**
     * Internal initializer called by Builder.build().
     * Uses double-checked locking to ensure only one instance is created and safely published.
     */
    private static ThreadSafeSingleton init(Builder b) {
        ThreadSafeSingleton s = instance; // first read (fast-path)
        if (s == null) {
            synchronized (ThreadSafeSingleton.class) {
                s = instance; // re-read inside lock
                if (s == null) {
                    instance = new ThreadSafeSingleton(b); // safe publication because 'instance' is volatile
                }
            }
        }
        return instance;
    }

    // --- getters ---
    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isFeatureEnabled() { return featureEnabled; }
    public long getTimeoutMs() { return timeoutMs; }

    // --- Builder ---
    public static final class Builder {
        private String host = "localhost";
        private int port = 80;
        private boolean featureEnabled = false;
        private long timeoutMs = 1000L;

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
         * If another thread already initialized the instance, this returns that instance
         * and ignores the current Builder's values.
         */
        public ThreadSafeSingleton build() {
            return ThreadSafeSingleton.init(this);
        }
    }

    // Optional: allow safe reconfiguration attempt detection. Uncomment to enable throwing on conflict.
    // private static void validateOrThrow(Builder b) {
    //     ThreadSafeSingleton s = instance;
    //     if (s != null) {
    //         if (!s.host.equals(b.host) || s.port != b.port || s.featureEnabled != b.featureEnabled || s.timeoutMs != b.timeoutMs) {
    //             throw new IllegalStateException("Singleton already initialized with different configuration");
    //         }
    //     }
    // }
}
