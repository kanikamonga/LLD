package LLD.multithreading.warehouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory warehouse with a fixed set of shelves numbered 0..n-1.
 * Shelves start empty, may hold multiple products, and have no capacity limit.
 * Restocking locks one shelf; fulfillment locks all involved shelves in ID order.
 * Inventory is private: callers can only inspect immutable, consistent snapshots.
 */
public final class Warehouse {
    private static final class Shelf {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final Map<String, Stock> products = new HashMap<>();
    }

    private static final class Stock {
        private final long quantity;
        private final long restocked;
        private final long fulfilled;

        private Stock(long quantity, long restocked, long fulfilled) {
            this.quantity = quantity;
            this.restocked = restocked;
            this.fulfilled = fulfilled;
        }

        private long getQuantity() {
            return quantity;
        }

        private long getRestocked() {
            return restocked;
        }

        private long getFulfilled() {
            return fulfilled;
        }
    }

    public static final class StockSnapshot {
        private final long quantity;
        private final long restocked;
        private final long fulfilled;

        public StockSnapshot(long quantity, long restocked, long fulfilled) {
            this.quantity = quantity;
            this.restocked = restocked;
            this.fulfilled = fulfilled;
        }

        public long getQuantity() {
            return quantity;
        }

        public long getRestocked() {
            return restocked;
        }

        public long getFulfilled() {
            return fulfilled;
        }

        public boolean isConsistent() {
            return quantity >= 0 && fulfilled >= 0 && restocked >= fulfilled
                    && quantity == restocked - fulfilled;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StockSnapshot)) return false;
            StockSnapshot stock = (StockSnapshot) other;
            return quantity == stock.quantity && restocked == stock.restocked
                    && fulfilled == stock.fulfilled;
        }

        @Override
        public int hashCode() {
            return Objects.hash(quantity, restocked, fulfilled);
        }

        @Override
        public String toString() {
            return "StockSnapshot[quantity=" + quantity + ", restocked=" + restocked
                    + ", fulfilled=" + fulfilled + "]";
        }
    }

    public static final class ShelfSnapshot {
        private final int shelfId;
        private final Map<String, StockSnapshot> products;

        public ShelfSnapshot(int shelfId, Map<String, StockSnapshot> products) {
            this.shelfId = shelfId;
            this.products = Map.copyOf(products);
        }

        public int getShelfId() {
            return shelfId;
        }

        public Map<String, StockSnapshot> getProducts() {
            return products;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ShelfSnapshot)) return false;
            ShelfSnapshot shelf = (ShelfSnapshot) other;
            return shelfId == shelf.shelfId && products.equals(shelf.products);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shelfId, products);
        }

        @Override
        public String toString() {
            return "ShelfSnapshot[shelfId=" + shelfId + ", products=" + products + "]";
        }
    }

    public static final class InsufficientStockException extends IllegalStateException {
        public InsufficientStockException(int shelfId, String productId, long requested, long available) {
            super("Shelf " + shelfId + ", product " + productId
                    + ": requested " + requested + ", available " + available);
        }
    }

    private final List<Shelf> shelves;

    public Warehouse(int shelfCount) {
        if (shelfCount <= 0) {
            throw new IllegalArgumentException("shelfCount must be positive");
        }
        List<Shelf> created = new ArrayList<>(shelfCount);
        for (int i = 0; i < shelfCount; i++) {
            created.add(new Shelf());
        }
        shelves = List.copyOf(created);
    }

    public void restock(int shelfId, String productId, long quantity) throws InterruptedException {
        validateShelf(shelfId);
        validateProduct(productId);
        validateQuantity(quantity);
        Shelf shelf = shelves.get(shelfId);
        shelf.lock.lockInterruptibly();
        try {
            Stock current = shelf.products.getOrDefault(productId, new Stock(0, 0, 0));
            // Construct the replacement before mutating; overflow cannot partially update stock.
            Stock updated = new Stock(Math.addExact(current.getQuantity(), quantity),
                    Math.addExact(current.getRestocked(), quantity), current.getFulfilled());
            shelf.products.put(productId, updated);
        } finally {
            shelf.lock.unlock();
        }
    }

    /**
     * request: shelfId -> (productId -> positive quantity).
     * Either every line is deducted or InsufficientStockException is thrown with no changes.
     * An unknown product has zero stock. Requests must not be mutated during copying.
     * Returns immutable fulfilled quantities. This is not an idempotent order-ID API:
     * each invocation represents a separate order.
     */
    public Map<Integer, Map<String, Long>> fulfillOrder(
            Map<Integer, Map<String, Long>> request) throws InterruptedException {
        TreeMap<Integer, Map<String, Long>> lines = validateAndCopy(request);
        List<Integer> ids = List.copyOf(lines.keySet());
        int acquired = 0;
        try {
            for (int id : ids) {
                shelves.get(id).lock.lockInterruptibly();
                acquired++;
            }

            Map<Integer, Map<String, Stock>> replacements = new HashMap<>();
            for (int id : ids) {
                Shelf shelf = shelves.get(id);
                Map<String, Stock> updated = new HashMap<>();
                for (Map.Entry<String, Long> line : lines.get(id).entrySet()) {
                    Stock current = shelf.products.getOrDefault(line.getKey(), new Stock(0, 0, 0));
                    long requested = line.getValue();
                    if (current.getQuantity() < requested) {
                        throw new InsufficientStockException(id, line.getKey(), requested, current.getQuantity());
                    }
                    updated.put(line.getKey(), new Stock(current.getQuantity() - requested,
                            current.getRestocked(), Math.addExact(current.getFulfilled(), requested)));
                }
                replacements.put(id, updated);
            }

            // All validation succeeded. Keep every lock until the entire order is committed.
            for (int id : ids) {
                shelves.get(id).products.putAll(replacements.get(id));
            }
            return Map.copyOf(lines);
        } finally {
            // Includes interruption while acquiring the second or later lock.
            for (int i = acquired - 1; i >= 0; i--) {
                shelves.get(ids.get(i)).lock.unlock();
            }
        }
    }

    /**
     * Locks all shelves in the same order as fulfillment, capturing a global point-in-time view.
     * A shelf-by-shelf unlocked-between-reads scan could see half of an atomic order.
     */
    public List<ShelfSnapshot> snapshot() throws InterruptedException {
        int acquired = 0;
        try {
            for (Shelf shelf : shelves) {
                shelf.lock.lockInterruptibly();
                acquired++;
            }
            List<ShelfSnapshot> result = new ArrayList<>();
            for (int id = 0; id < shelves.size(); id++) {
                Map<String, StockSnapshot> products = new HashMap<>();
                shelves.get(id).products.forEach((product, stock) -> products.put(product,
                        new StockSnapshot(stock.getQuantity(), stock.getRestocked(), stock.getFulfilled())));
                result.add(new ShelfSnapshot(id, products));
            }
            return List.copyOf(result);
        } finally {
            for (int i = acquired - 1; i >= 0; i--) {
                shelves.get(i).lock.unlock();
            }
        }
    }

    private TreeMap<Integer, Map<String, Long>> validateAndCopy(Map<Integer, Map<String, Long>> request) {
        if (request == null || request.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        TreeMap<Integer, Map<String, Long>> copy = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, Long>> entry : request.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("shelfId must not be null");
            }
            validateShelf(entry.getKey());
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("Each requested shelf must contain order items");
            }
            Map<String, Long> products = new HashMap<>();
            for (Map.Entry<String, Long> product : entry.getValue().entrySet()) {
                validateProduct(product.getKey());
                if (product.getValue() == null) {
                    throw new IllegalArgumentException("quantity must not be null");
                }
                validateQuantity(product.getValue());
                products.put(product.getKey(), product.getValue());
            }
            copy.put(entry.getKey(), Map.copyOf(products));
        }
        return copy;
    }

    private void validateShelf(int id) {
        if (id < 0 || id >= shelves.size()) {
            throw new IllegalArgumentException("Unknown shelf: " + id);
        }
    }

    private static void validateProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
    }

    private static void validateQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
