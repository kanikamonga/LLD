package LLD.foodordering;

import LLD.foodordering.exceptions.InvalidOrderStateException;
import LLD.foodordering.exceptions.NoRestaurantAvailableException;
import LLD.foodordering.exceptions.OrderNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places orders (auto-assigning them to a restaurant via a pluggable
 * {@link RestaurantSelectionStrategy}) and tracks their lifecycle
 * (PENDING -> ACCEPTED -> COMPLETED, or PENDING -> REJECTED).
 *
 * Kept separate from RestaurantService: this class only cares about
 * "given the current set of restaurants, assign/complete orders"; it does
 * not know how restaurants are onboarded or how menus are edited.
 */
public final class OrderService {

    private final RestaurantService restaurantService;
    private final Map<Long, Order> ordersById = new ConcurrentHashMap<>();

    public OrderService(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    /**
     * Places a new order and attempts to auto-assign it to a restaurant.
     *
     * @throws NoRestaurantAvailableException if no restaurant's menu covers every
     *         requested item, or every restaurant that could serve it is at full capacity.
     *         The order is still recorded (in REJECTED state) for audit/history purposes.
     */
    public Order placeOrder(String customerName, Map<String, Integer> items, RestaurantSelectionStrategy strategy) {
        Order order = new Order(customerName, items, strategy);
        ordersById.put(order.getId(), order);
        assignToRestaurant(order);
        return order;
    }

    private void assignToRestaurant(Order order) {
        // Step 1: an order can only be auto-assigned if a SINGLE restaurant's
        // menu covers every requested item - partial fulfillment across
        // multiple restaurants is not supported by the platform.
        List<Restaurant> eligible = restaurantService.getAllRestaurants().stream()
                .filter(r -> r.canFulfill(order.getItems()))
                .toList();

        if (eligible.isEmpty()) {
            order.markRejected();
            throw new NoRestaurantAvailableException(
                    "Order " + order.getId() + " cannot be fulfilled: no restaurant's menu covers all requested items "
                            + order.getItems().keySet());
        }

        // Step 2: rank eligible restaurants per the caller-chosen strategy
        // (lowest cost, highest rating, ... - pluggable, see RestaurantSelectionStrategy).
        List<Restaurant> ranked = order.getSelectionStrategy().rank(eligible, order.getItems());

        // Step 3: walk the ranked list and try to atomically reserve capacity.
        // If the top choice is already full, fall through to the next-best
        // restaurant instead of failing outright - capacity and ranking are
        // independent concerns.
        for (Restaurant restaurant : ranked) {
            if (restaurant.tryReserveCapacity()) {
                order.markAccepted(restaurant);
                return;
            }
        }

        // Every restaurant that COULD serve this order is currently full.
        order.markRejected();
        throw new NoRestaurantAvailableException(
                "Order " + order.getId() + " cannot be fulfilled right now: all restaurants that serve "
                        + order.getItems().keySet() + " are at full capacity");
    }

    /**
     * Restaurant marks an ACCEPTED order as COMPLETED, freeing up its processing capacity.
     * Per requirements, this is the ONLY status transition a restaurant may perform on an
     * order - restaurants cannot cancel an already-ACCEPTED order.
     */
    public Order completeOrder(long orderId) {
        Order order = getOrder(orderId);
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " cannot be completed from status " + order.getStatus());
        }
        order.markCompleted();
        order.getAssignedRestaurant().releaseCapacity();
        return order;
    }

    public Order getOrder(long orderId) {
        Order order = ordersById.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order " + orderId + " not found");
        }
        return order;
    }
}
