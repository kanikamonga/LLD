package LLD.shoppingcart;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a shopping cart belonging to a user/session.
 * Not thread-safe by itself; CartService manages concurrency.
 */
public class Cart {
    private final String id;
    private final Map<String, CartItem> items = new LinkedHashMap<>();
    private final int maxItems;

    public Cart(String id, int maxItems) {
        this.id = id;
        this.maxItems = maxItems;
    }

    public String getId() { return id; }

    public Map<String, CartItem> viewItems() { return Collections.unmodifiableMap(items); }

    public int totalItems() {
        return items.values().stream().mapToInt(CartItem::getQuantity).sum();
    }

    public void addItem(Product product, int quantity) {
        if (items.size() >= maxItems && !items.containsKey(product.getId())) {
            throw new IllegalStateException("Cart item limit reached");
        }
        CartItem existing = items.get(product.getId());
        if (existing == null) {
            items.put(product.getId(), new CartItem(product, quantity));
        } else {
            existing.setQuantity(existing.getQuantity() + quantity);
        }
    }

    public void removeItem(String productId) {
        items.remove(productId);
    }

    public boolean isEmpty() { return items.isEmpty(); }
}
