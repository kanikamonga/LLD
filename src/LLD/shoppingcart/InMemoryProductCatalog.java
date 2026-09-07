package LLD.shoppingcart;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory product catalog with stock counts.
 */
public class InMemoryProductCatalog implements ProductCatalog {
    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> stock = new ConcurrentHashMap<>();

    public void addProduct(Product p, int qty) {
        products.put(p.getId(), p);
        stock.put(p.getId(), new AtomicInteger(qty));
    }

    @Override public Product getProduct(String productId) { return products.get(productId); }

    @Override public int availableQuantity(String productId) {
        AtomicInteger a = stock.get(productId);
        return a == null ? 0 : Math.max(0, a.get());
    }

    @Override public boolean reserve(String productId, int quantity) {
        AtomicInteger a = stock.get(productId);
        if (a == null) return false;
        while (true) {
            int cur = a.get();
            if (cur < quantity) return false;
            if (a.compareAndSet(cur, cur - quantity)) return true;
        }
    }

    @Override public void release(String productId, int quantity) {
        AtomicInteger a = stock.get(productId);
        if (a == null) return;
        a.addAndGet(quantity);
    }
}
