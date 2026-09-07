package LLD.foodordering;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An order placed by a customer. Mutable only in its status and assigned
 * restaurant (both guarded here so status transitions stay consistent even
 * if inspected concurrently); the requested items themselves are immutable
 * once the order is created.
 */
public final class Order {
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private final long id;
    private final String customerName;
    private final Map<String, Integer> items; // itemName -> quantity
    private final RestaurantSelectionStrategy selectionStrategy;

    private volatile OrderStatus status;
    private volatile Restaurant assignedRestaurant; // null until ACCEPTED

    public Order(String customerName, Map<String, Integer> items, RestaurantSelectionStrategy selectionStrategy) {
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("customerName must not be blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                throw new IllegalArgumentException("Quantity for '" + e.getKey() + "' must be > 0");
            }
        }
        this.id = SEQUENCE.incrementAndGet();
        this.customerName = customerName;
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        this.selectionStrategy = selectionStrategy;
        this.status = OrderStatus.PENDING;
    }

    public long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Map<String, Integer> getItems() {
        return items;
    }

    public RestaurantSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Restaurant getAssignedRestaurant() {
        return assignedRestaurant;
    }

    void markAccepted(Restaurant restaurant) {
        this.assignedRestaurant = restaurant;
        this.status = OrderStatus.ACCEPTED;
    }

    void markCompleted() {
        this.status = OrderStatus.COMPLETED;
    }

    void markRejected() {
        this.status = OrderStatus.REJECTED;
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", customer=" + customerName + ", items=" + items + ", status=" + status
                + ", restaurant=" + (assignedRestaurant == null ? "none" : assignedRestaurant.getName()) + "}";
    }
}
