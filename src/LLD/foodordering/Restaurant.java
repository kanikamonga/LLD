package LLD.foodordering;

import LLD.foodordering.exceptions.MenuItemNotFoundException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A restaurant onboarded onto the platform.
 *
 * Responsibilities (single responsibility principle):
 *  - Own its menu (add/update items - items can never be deleted).
 *  - Own its processing capacity (how many ACCEPTED orders it can hold
 *    concurrently) and enforce that limit atomically.
 *  - Answer "can I fulfill this set of items?" and "what would it cost?"
 *
 * It deliberately does NOT know about Orders, OrderService, or selection
 * strategies - those are separate concerns (kept in OrderService and the
 * RestaurantSelectionStrategy implementations).
 *
 * Thread-safety: menu updates and capacity accounting are synchronized via an
 * internal lock so concurrent order placements/menu edits cannot corrupt
 * state or over-allocate capacity (see tryReserveCapacity()).
 */
public final class Restaurant {

    private final String name;
    private final int maxConcurrentOrders;
    private final Map<String, MenuItem> menu = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    private double rating; // 1.0 - 5.0
    private int currentActiveOrders = 0;

    public Restaurant(String name, int maxConcurrentOrders, double rating) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Restaurant name must not be blank");
        }
        if (maxConcurrentOrders <= 0) {
            throw new IllegalArgumentException("maxConcurrentOrders must be > 0");
        }
        validateRating(rating);
        this.name = name;
        this.maxConcurrentOrders = maxConcurrentOrders;
        this.rating = rating;
    }

    private static void validateRating(double rating) {
        if (rating < 1.0 || rating > 5.0) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
    }

    public String getName() {
        return name;
    }

    public double getRating() {
        lock.lock();
        try {
            return rating;
        } finally {
            lock.unlock();
        }
    }

    public void setRating(double rating) {
        validateRating(rating);
        lock.lock();
        try {
            this.rating = rating;
        } finally {
            lock.unlock();
        }
    }

    public int getMaxConcurrentOrders() {
        return maxConcurrentOrders;
    }

    public int getCurrentActiveOrders() {
        lock.lock();
        try {
            return currentActiveOrders;
        } finally {
            lock.unlock();
        }
    }

    /** Adds a brand-new menu item. Fails if an item with this name already exists (use updateItemPrice instead). */
    public void addMenuItem(String itemName, double price) {
        lock.lock();
        try {
            if (menu.containsKey(itemName)) {
                throw new IllegalArgumentException(
                        "Item '" + itemName + "' already exists on " + name + "'s menu; use updateItemPrice() to change its price");
            }
            menu.put(itemName, new MenuItem(itemName, price));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the price of an existing item. Per requirements, items can never be
     * deleted - only added or price-updated.
     */
    public void updateItemPrice(String itemName, double newPrice) {
        lock.lock();
        try {
            MenuItem item = menu.get(itemName);
            if (item == null) {
                throw new MenuItemNotFoundException(
                        "Item '" + itemName + "' does not exist on " + name + "'s menu; add it first");
            }
            item.updatePrice(newPrice);
        } finally {
            lock.unlock();
        }
    }

    /** Read-only snapshot of the current menu. */
    public Map<String, MenuItem> getMenu() {
        lock.lock();
        try {
            return Collections.unmodifiableMap(new LinkedHashMap<>(menu));
        } finally {
            lock.unlock();
        }
    }

    /** Whether this restaurant's menu contains every requested item. */
    public boolean canFulfill(Map<String, Integer> requestedItems) {
        lock.lock();
        try {
            for (String itemName : requestedItems.keySet()) {
                if (!menu.containsKey(itemName)) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Total cost to fulfill the requested items at this restaurant. Caller must have checked canFulfill() first. */
    public double quoteCost(Map<String, Integer> requestedItems) {
        lock.lock();
        try {
            double total = 0.0;
            for (Map.Entry<String, Integer> req : requestedItems.entrySet()) {
                MenuItem item = menu.get(req.getKey());
                if (item == null) {
                    throw new MenuItemNotFoundException("Item '" + req.getKey() + "' not found on " + name + "'s menu");
                }
                total += item.getPrice() * req.getValue();
            }
            return total;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically checks-and-reserves one unit of processing capacity.
     * Returns true if capacity was available and is now reserved (caller must
     * later call releaseCapacity() when the order completes); false if the
     * restaurant is already at maxConcurrentOrders.
     *
     * This is the crux of the concurrency handling: without doing the
     * check-then-increment atomically under the same lock, two threads could
     * both see "1 slot free" and both proceed, over-booking the restaurant.
     */
    public boolean tryReserveCapacity() {
        lock.lock();
        try {
            if (currentActiveOrders >= maxConcurrentOrders) {
                return false;
            }
            currentActiveOrders++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Frees up one unit of capacity, called when an ACCEPTED order is marked COMPLETED. */
    public void releaseCapacity() {
        lock.lock();
        try {
            if (currentActiveOrders > 0) {
                currentActiveOrders--;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "Restaurant{" + name + ", rating=" + getRating() + ", active=" + getCurrentActiveOrders()
                + "/" + maxConcurrentOrders + "}";
    }
}
