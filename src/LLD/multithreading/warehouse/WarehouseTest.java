package LLD.multithreading.warehouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Dependency-free functional and concurrent tests. */
public final class WarehouseTest {
    public static void main(String[] args) throws Exception {
        rejectsWithoutPartialDeduction();
        validatesAndHandlesOverflow();
        concurrentWorkAndMonitoring();
        lastStockCannotBeSoldTwice();
        detectsInconsistentSnapshot();
        snapshotClassesPreserveValueSemantics();
        System.out.println("All warehouse tests passed");
    }

    private static void rejectsWithoutPartialDeduction() throws Exception {
        Warehouse warehouse = new Warehouse(2);
        warehouse.restock(0, "a", 10);
        warehouse.restock(1, "b", 1);
        List<Warehouse.ShelfSnapshot> before = warehouse.snapshot();
        try {
            warehouse.fulfillOrder(Map.of(0, Map.of("a", 5L), 1, Map.of("b", 2L)));
            throw new AssertionError("Expected insufficient stock");
        } catch (Warehouse.InsufficientStockException expected) {
            equal(before, warehouse.snapshot());
        }
        warehouse.fulfillOrder(Map.of(0, Map.of("a", 5L), 1, Map.of("b", 1L)));
        equal(5L, warehouse.snapshot().get(0).getProducts().get("a").getQuantity());
        equal(0L, warehouse.snapshot().get(1).getProducts().get("b").getQuantity());
        // A snapshot remains unchanged after subsequent operations.
        equal(10L, before.get(0).getProducts().get("a").getQuantity());
    }

    private static void validatesAndHandlesOverflow() throws Exception {
        Warehouse warehouse = new Warehouse(1);
        expectInvalid(() -> warehouse.restock(1, "a", 1));
        expectInvalid(() -> warehouse.restock(0, " ", 1));
        expectInvalid(() -> warehouse.restock(0, "a", 0));
        expectInvalid(() -> warehouse.fulfillOrder(Map.of()));
        expectInvalid(() -> warehouse.fulfillOrder(Map.of(0, Map.of("a", -1L))));
        warehouse.restock(0, "a", Long.MAX_VALUE);
        List<Warehouse.ShelfSnapshot> before = warehouse.snapshot();
        try {
            warehouse.restock(0, "a", 1);
            throw new AssertionError("Expected overflow");
        } catch (ArithmeticException expected) {
            equal(before, warehouse.snapshot());
        }
    }

    private static void concurrentWorkAndMonitoring() throws Exception {
        Warehouse warehouse = new Warehouse(2);
        warehouse.restock(0, "a", 1_000);
        warehouse.restock(1, "b", 1_000);
        List<Callable<Void>> work = new ArrayList<>();
        for (int worker = 0; worker < 8; worker++) {
            final boolean reverse = worker % 2 == 0;
            work.add(() -> {
                Map<Integer, Map<String, Long>> request = new LinkedHashMap<>();
                request.put(reverse ? 1 : 0, Map.of(reverse ? "b" : "a", 1L));
                request.put(reverse ? 0 : 1, Map.of(reverse ? "a" : "b", 1L));
                for (int i = 0; i < 100; i++) {
                    warehouse.fulfillOrder(request);
                }
                return null;
            });
        }
        for (int worker = 0; worker < 4; worker++) {
            work.add(() -> {
                for (int i = 0; i < 100; i++) {
                    warehouse.restock(0, "a", 1);
                    warehouse.restock(1, "b", 1);
                }
                return null;
            });
        }
        work.add(() -> {
            for (int i = 0; i < 100; i++) {
                List<Warehouse.ShelfSnapshot> snapshot = warehouse.snapshot();
                InventoryMonitor.verify(snapshot);
                equal(snapshot.get(0).getProducts().get("a").getFulfilled(),
                        snapshot.get(1).getProducts().get("b").getFulfilled());
            }
            return null;
        });
        try (InventoryMonitor monitor = new InventoryMonitor(warehouse, 1)) {
            runTogether(work);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (monitor.successfulChecks() == 0 && System.nanoTime() < deadline) {
                monitor.checkHealthy();
                Thread.sleep(1);
            }
            if (monitor.successfulChecks() == 0) {
                throw new AssertionError("Periodic monitor did not run");
            }
        }
        List<Warehouse.ShelfSnapshot> result = warehouse.snapshot();
        InventoryMonitor.verify(result);
        for (Warehouse.ShelfSnapshot shelf : result) {
            Warehouse.StockSnapshot stock = shelf.getProducts().values().iterator().next();
            equal(600L, stock.getQuantity());
            equal(1_400L, stock.getRestocked());
            equal(800L, stock.getFulfilled());
        }
    }

    private static void lastStockCannotBeSoldTwice() throws Exception {
        Warehouse warehouse = new Warehouse(1);
        warehouse.restock(0, "a", 1);
        List<Callable<Boolean>> work = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            work.add(() -> {
                try {
                    warehouse.fulfillOrder(Map.of(0, Map.of("a", 1L)));
                    return true;
                } catch (Warehouse.InsufficientStockException expected) {
                    return false;
                }
            });
        }
        equal(1L, runTogether(work).stream().filter(Boolean::booleanValue).count());
    }

    private static void detectsInconsistentSnapshot() {
        try {
            InventoryMonitor.verify(List.of(new Warehouse.ShelfSnapshot(
                    0, Map.of("a", new Warehouse.StockSnapshot(5, 10, 2)))));
            throw new AssertionError("Expected consistency failure");
        } catch (IllegalStateException expected) {
            // The monitor must report a broken ledger rather than repair it silently.
        }

    }

    private static void snapshotClassesPreserveValueSemantics() {
        Warehouse.StockSnapshot stock = new Warehouse.StockSnapshot(8, 10, 2);
        Warehouse.StockSnapshot equivalent = new Warehouse.StockSnapshot(8, 10, 2);
        equal(stock, equivalent);
        equal(stock.hashCode(), equivalent.hashCode());
        Map<String, Warehouse.StockSnapshot> products = new LinkedHashMap<>();
        products.put("a", stock);
        Warehouse.ShelfSnapshot shelf = new Warehouse.ShelfSnapshot(0, products);
        products.clear();
        equal(0, shelf.getShelfId());
        equal(Map.of("a", stock), shelf.getProducts());
        Warehouse.ShelfSnapshot sameShelf = new Warehouse.ShelfSnapshot(0, Map.of("a", equivalent));
        equal(shelf, sameShelf);
        equal(shelf.hashCode(), sameShelf.hashCode());
        try {
            shelf.getProducts().clear();
            throw new AssertionError("Snapshot products must be immutable");
        } catch (UnsupportedOperationException expected) {
            // External callers cannot change snapshot contents.
        }
    }

    private static <T> List<T> runTogether(List<Callable<T>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : work) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            for (Future<T> future : futures) {
                results.add(future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Worker shutdown timed out (possible deadlock)");
            }
        }
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run() throws Exception;
    }

    private static void expectInvalid(CheckedOperation task) throws Exception {
        try {
            task.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected invalid-input exception");
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", received " + actual);
        }
    }
}
