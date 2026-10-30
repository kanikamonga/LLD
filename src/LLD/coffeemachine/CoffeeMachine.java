package LLD.coffeemachine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Design: N executor workers are the outlets; excess requests wait in the executor queue.
 * Inventory transitions alone share a short monitor. Preparation never holds that monitor.
 *
 * Reservation algorithm: scan ALL missing ingredients before scanning insufficient quantities,
 * then deduct the entire recipe atomically. Track reservations separately so a concurrent refill
 * cannot occupy space needed for a rollback (even at Long.MAX_VALUE).
 * A successful preparation commits consumption; interruption or hook failure restores the entire
 * reservation atomically. Worker exceptions propagate through Future.get(), never disappear.
 *
 * For K recipe ingredients, reservation/commit/rollback take O(K) time, O(K) reservation space.
 * Inventory snapshots/low indicators take O(I) time and space for I stocked ingredients.
 */
public final class CoffeeMachine implements AutoCloseable {
    @FunctionalInterface
    public interface Preparation {
        void prepare(String beverage, long durationMillis) throws Exception;
    }

    public enum Status { PREPARED, UNAVAILABLE, INSUFFICIENT, UNKNOWN_BEVERAGE }

    public static final class Result {
        private final Status status;
        private final String message;

        private Result(Status status, String message) {
            this.status = status;
            this.message = message;
        }

        public Status status() { return status; }
        public String message() { return message; }
        @Override public String toString() { return message; }
    }

    private final Object lock = new Object();
    private final Map<String, Long> stock;
    private final Map<String, Long> reserved = new LinkedHashMap<>();
    private final Map<String, Map<String, Long>> recipes;
    private final ExecutorService outlets;
    private final long durationMillis;
    private final long lowThreshold;
    private final Preparation preparation;
    private boolean closed;

    public CoffeeMachine(MachineConfig config, long durationMillis, long lowThreshold) {
        this(config, durationMillis, lowThreshold, (name, duration) -> Thread.sleep(duration));
    }

    public CoffeeMachine(MachineConfig config, long durationMillis, long lowThreshold,
                         Preparation preparation) {
        Objects.requireNonNull(config, "config");
        if (durationMillis < 0 || lowThreshold < 0)
            throw new IllegalArgumentException("duration and low threshold must be nonnegative");
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.durationMillis = durationMillis;
        this.lowThreshold = lowThreshold;
        stock = new LinkedHashMap<>(config.inventory());
        recipes = config.beverages();
        outlets = Executors.newFixedThreadPool(config.outlets());
    }

    public Future<Result> order(String beverage) {
        MachineConfig.requireName(beverage);
        synchronized (lock) {
            requireOpen();
            return outlets.submit(() -> prepare(beverage));
        }
    }

    private Result prepare(String beverage) throws Exception {
        Map<String, Long> recipe = recipes.get(beverage);
        if (recipe == null)
            return new Result(Status.UNKNOWN_BEVERAGE, beverage + " is not a known beverage");
        synchronized (lock) {
            for (String ingredient : recipe.keySet()) {
                if (!stock.containsKey(ingredient))
                    return new Result(Status.UNAVAILABLE, beverage
                            + " cannot be prepared because " + ingredient + " is not available");
            }
            for (Map.Entry<String, Long> item : recipe.entrySet()) {
                if (stock.get(item.getKey()) < item.getValue())
                    return new Result(Status.INSUFFICIENT, beverage
                            + " cannot be prepared because item " + item.getKey() + " is not sufficient");
            }
            recipe.forEach((name, amount) -> {
                stock.put(name, stock.get(name) - amount);
                reserved.put(name, Math.addExact(reserved.getOrDefault(name, 0L), amount));
            });
        }
        boolean success = false;
        try {
            preparation.prepare(beverage, durationMillis);
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException("preparation interrupted");
            success = true;
            return new Result(Status.PREPARED, beverage + " is prepared");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            synchronized (lock) {
                final boolean commit = success;
                recipe.forEach((name, amount) -> {
                    if (!commit) stock.put(name, Math.addExact(stock.get(name), amount));
                    reserved.put(name, reserved.get(name) - amount);
                });
            }
        }
    }

    /** Adds a positive amount, including a previously unavailable ingredient. */
    public void refill(String ingredient, long amount) {
        MachineConfig.requireName(ingredient);
        if (amount <= 0) throw new IllegalArgumentException("refill amount must be positive");
        synchronized (lock) {
            requireOpen();
            long current = stock.getOrDefault(ingredient, 0L);
            Math.addExact(Math.addExact(current, reserved.getOrDefault(ingredient, 0L)), amount);
            stock.put(ingredient, Math.addExact(current, amount));
        }
    }

    public Map<String, Long> inventory() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(stock));
        }
    }

    /** Inclusive threshold on available (not reserved) stock; missing recipe items appear as zero. */
    public Map<String, Long> lowIngredients() {
        synchronized (lock) {
            Map<String, Long> low = new LinkedHashMap<>();
            stock.forEach((name, amount) -> { if (amount <= lowThreshold) low.put(name, amount); });
            recipes.values().forEach(recipe -> recipe.keySet().forEach(name -> {
                if (!stock.containsKey(name)) low.put(name, 0L);
            }));
            return Collections.unmodifiableMap(low);
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("coffee machine is closed");
    }

    /** Stops acceptance; already queued orders drain normally. Idempotent and nonblocking. */
    @Override public void close() {
        synchronized (lock) {
            closed = true;
            outlets.shutdown();
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return outlets.awaitTermination(timeout, unit);
    }
}
