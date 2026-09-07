package LLD.foodordering;

import LLD.foodordering.exceptions.DuplicateRestaurantException;
import LLD.foodordering.exceptions.RestaurantNotFoundException;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the registry of onboarded restaurants: onboarding new restaurants and
 * routing menu-update requests to the right one. Kept separate from
 * OrderService so restaurant/menu management and order-placement/assignment
 * remain independent concerns (single responsibility).
 */
public final class RestaurantService {

    // Restaurant names are assumed unique (per problem statement).
    private final Map<String, Restaurant> restaurantsByName = new ConcurrentHashMap<>();

    public Restaurant onboardRestaurant(String name, int maxConcurrentOrders, double rating,
                                        Map<String, Double> initialMenu) {
        Restaurant restaurant = new Restaurant(name, maxConcurrentOrders, rating);
        Restaurant existing = restaurantsByName.putIfAbsent(name, restaurant);
        if (existing != null) {
            throw new DuplicateRestaurantException("Restaurant '" + name + "' is already onboarded");
        }
        if (initialMenu != null) {
            initialMenu.forEach(restaurant::addMenuItem);
        }
        return restaurant;
    }

    public Restaurant getRestaurant(String name) {
        Restaurant restaurant = restaurantsByName.get(name);
        if (restaurant == null) {
            throw new RestaurantNotFoundException("Restaurant '" + name + "' not found");
        }
        return restaurant;
    }

    /** Adds a brand-new item to a restaurant's menu. */
    public void addMenuItem(String restaurantName, String itemName, double price) {
        getRestaurant(restaurantName).addMenuItem(itemName, price);
    }

    /** Updates the price of an existing menu item (items can never be deleted). */
    public void updateMenuItemPrice(String restaurantName, String itemName, double newPrice) {
        getRestaurant(restaurantName).updateItemPrice(itemName, newPrice);
    }

    public Collection<Restaurant> getAllRestaurants() {
        return restaurantsByName.values();
    }
}
