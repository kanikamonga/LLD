package LLD.multithreading.warehouse;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One periodic monitor thread. It validates snapshots after shelf locks are released.
 * The ledger invariant is quantity = total restocked - total fulfilled per product.
 * This detects bookkeeping inconsistencies, not discrepancies with physical goods.
 */
public final class InventoryMonitor implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(InventoryMonitor.class.getName());
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> task;
    private final AtomicLong successfulChecks = new AtomicLong();
    private volatile Throwable failure;

    public InventoryMonitor(Warehouse warehouse, long intervalMillis) {
        Objects.requireNonNull(warehouse, "warehouse");
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("intervalMillis must be positive");
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, "inventory-monitor"));
        task = scheduler.scheduleWithFixedDelay(() -> {
            try {
                verify(warehouse.snapshot());
                successfulChecks.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Interruption is expected only when close() cancels the monitor.
            } catch (RuntimeException e) {
                failure = e;
                LOGGER.log(System.Logger.Level.ERROR, "Inventory consistency check failed", e);
                throw e; // Stop monitoring rather than repeatedly reporting a healthy system.
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public static void verify(List<Warehouse.ShelfSnapshot> snapshot) {
        for (Warehouse.ShelfSnapshot shelf : snapshot) {
            shelf.getProducts().forEach((product, stock) -> {
                if (!stock.isConsistent()) {
                    throw new IllegalStateException("Inconsistent inventory on shelf "
                            + shelf.getShelfId() + ", product " + product + ": " + stock);
                }
            });
        }
    }

    public long successfulChecks() {
        return successfulChecks.get();
    }

    public void checkHealthy() {
        if (failure != null) {
            throw new IllegalStateException("Inventory monitor failed", failure);
        }
    }

    @Override
    public void close() throws InterruptedException {
        task.cancel(true);
        scheduler.shutdownNow();
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Inventory monitor did not stop");
        }
        checkHealthy();
    }
}
