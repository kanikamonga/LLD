package LLD.EcommerceCheckout.inventory;

import LLD.EcommerceCheckout.model.CartItem;
import LLD.EcommerceCheckout.model.Order;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns stock reservation and release with atomic in-memory updates. */
public final class InventoryService {
    private final Map<String, Integer> available = new HashMap<>();
    private final Map<String, Map<String, Integer>> reservations = new HashMap<>();

    public synchronized void addStock(String productId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        available.put(productId, available.getOrDefault(productId, 0) + quantity);
    }

    public synchronized void reserve(String reservationId, List<CartItem> items) {
        Map<String, Integer> requested = quantities(items);
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                throw new InsufficientStockException("Insufficient stock: " + entry.getKey());
            }
        }
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            available.put(entry.getKey(), available.get(entry.getKey()) - entry.getValue());
        }
        reservations.put(reservationId, requested);
    }

    public synchronized void commit(String reservationId) {
        requireReservation(reservationId);
        reservations.remove(reservationId);
    }

    public synchronized void release(String reservationId) {
        Map<String, Integer> reserved = reservations.remove(reservationId);
        if (reserved != null) {
            for (Map.Entry<String, Integer> entry : reserved.entrySet()) {
                available.put(entry.getKey(),
                        available.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }
        }
    }

    public synchronized int available(String productId) {
        return available.getOrDefault(productId, 0);
    }

    private void requireReservation(String reservationId) {
        if (!reservations.containsKey(reservationId)) {
            throw new IllegalStateException("Reservation not found: " + reservationId);
        }
    }

    private static Map<String, Integer> quantities(List<CartItem> items) {
        Map<String, Integer> result = new HashMap<>();
        for (CartItem item : items) {
            result.put(item.productId(),
                    result.getOrDefault(item.productId(), 0) + item.quantity());
        }
        return result;
    }

    public static final class InsufficientStockException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private InsufficientStockException(String message) { super(message); }
    }
}
