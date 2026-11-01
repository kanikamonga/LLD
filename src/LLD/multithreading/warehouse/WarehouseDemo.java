package LLD.multithreading.warehouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Worker-pool tasks represent restocking and order fulfillment; failures propagate via Futures. */
public final class WarehouseDemo {
    public static void main(String[] args) throws Exception {
        Warehouse warehouse = new Warehouse(3);
        warehouse.restock(0, "book", 100);
        warehouse.restock(1, "pen", 100);
        warehouse.restock(2, "box", 100);

        ExecutorService workers = Executors.newFixedThreadPool(6);
        try (InventoryMonitor monitor = new InventoryMonitor(warehouse, 20)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                tasks.add(workers.submit(() -> {
                    warehouse.restock(2, "box", 2);
                    return null;
                }));
                tasks.add(workers.submit(() -> warehouse.fulfillOrder(
                        Map.of(0, Map.of("book", 2L), 1, Map.of("pen", 1L)))));
            }
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            monitor.checkHealthy();
            List<Warehouse.ShelfSnapshot> snapshot = warehouse.snapshot();
            InventoryMonitor.verify(snapshot);
            snapshot.forEach(System.out::println);
            System.out.println("Periodic consistency checks: " + monitor.successfulChecks());
        } finally {
            workers.shutdownNow();
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers did not stop");
            }
        }
    }
}
