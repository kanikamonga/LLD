package LLD.shoppingcart;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that manages carts and coordinates with ProductCatalog for validations/reservations.
 * Thread-safe: uses per-cart locking via ConcurrentHashMap of locks.
 */
public class CartService {
    private final ProductCatalog catalog;
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final int defaultMaxItems;

    public CartService(ProductCatalog catalog, int defaultMaxItems) {
        this.catalog = catalog;
        this.defaultMaxItems = defaultMaxItems;
    }

    public Cart createCart(String cartId) {
        Cart c = new Cart(cartId, defaultMaxItems);
        carts.put(cartId, c);
        locks.put(cartId, new Object());
        return c;
    }

    private Object lockFor(String cartId) {
        return locks.computeIfAbsent(cartId, k -> new Object());
    }

    public void addItem(String cartId, String productId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        Product p = catalog.getProduct(productId);
        if (p == null) throw new IllegalArgumentException("product not found");
        int available = catalog.availableQuantity(productId);
        if (available < quantity) throw new IllegalStateException("insufficient stock");

        Cart cart = carts.computeIfAbsent(cartId, id -> new Cart(id, defaultMaxItems));
        synchronized (lockFor(cartId)) {
            cart.addItem(p, quantity);
        }
    }

    public void removeItem(String cartId, String productId) {
        Cart cart = carts.get(cartId);
        if (cart == null) return;
        synchronized (lockFor(cartId)) {
            cart.removeItem(productId);
        }
    }

    public Map<String, CartItem> viewCart(String cartId) {
        Cart cart = carts.get(cartId);
        if (cart == null) return Map.of();
        synchronized (lockFor(cartId)) {
            return cart.viewItems();
        }
    }

    public void checkout(String cartId) {
        Cart cart = carts.get(cartId);
        if (cart == null || cart.isEmpty()) throw new IllegalStateException("Cart is empty");
        synchronized (lockFor(cartId)) {
            // reserve items in catalog
            for (CartItem ci : cart.viewItems().values()) {
                boolean ok = catalog.reserve(ci.getProduct().getId(), ci.getQuantity());
                if (!ok) {
                    // rollback previously reserved
                    for (CartItem prev : cart.viewItems().values()) {
                        if (prev == ci) break;
                        catalog.release(prev.getProduct().getId(), prev.getQuantity());
                    }
                    throw new IllegalStateException("Failed to reserve product: " + ci.getProduct().getId());
                }
            }
            // success: finalize order (omitted). Clear cart
            for (CartItem ci : cart.viewItems().values()) {
                cart.removeItem(ci.getProduct().getId());
            }
        }
    }
}
