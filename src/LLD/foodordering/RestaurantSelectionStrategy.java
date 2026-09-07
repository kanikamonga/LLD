package LLD.foodordering;

import java.util.List;
import java.util.Map;

/**
 * Strategy pattern: decides, among restaurants that CAN fulfill an order's
 * items, which one to prefer first when trying to assign the order.
 *
 * Implementations return candidates ordered from MOST to LEAST preferred.
 * OrderService then walks this ordered list and tries to reserve capacity on
 * each in turn, falling through to the next candidate if the preferred one is
 * full - this way "auto-assign by criteria X" and "respect capacity limits"
 * remain two independent, composable concerns.
 */
public interface RestaurantSelectionStrategy {

    /**
     * @param eligibleRestaurants restaurants whose menu covers every item in {@code requestedItems}
     *                            (capacity is NOT considered here - that's handled by the caller)
     * @param requestedItems      itemName -> quantity being ordered
     * @return eligibleRestaurants sorted from most to least preferred by this strategy
     */
    List<Restaurant> rank(List<Restaurant> eligibleRestaurants, Map<String, Integer> requestedItems);

    /** Human-readable name, useful for logging/demo output. */
    String name();
}
