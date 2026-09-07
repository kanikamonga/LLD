package LLD.foodordering;

import LLD.foodordering.exceptions.NoRestaurantAvailableException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Driver class that walks through the sample scenario from the problem
 * statement, printing the outcome of each step so the whole flow is
 * demoable end-to-end without a database or console input.
 */
public final class FoodOrderingDemo {

    public static void main(String[] args) {
        RestaurantService restaurantService = new RestaurantService();
        OrderService orderService = new OrderService(restaurantService);

        RestaurantSelectionStrategy lowestCost = new LowestCostStrategy();
        RestaurantSelectionStrategy highestRating = new HighestRatingStrategy();

        // ---- Onboard restaurants ----
        restaurantService.onboardRestaurant("R1", 5, 4.5, menu(
                "Veg Biryani", 100.0,
                "Paneer Butter Masala", 150.0));

        restaurantService.onboardRestaurant("R2", 5, 4.0, menu(
                "Paneer Butter Masala", 175.0,
                "Idli", 10.0,
                "Dosa", 50.0,
                "Veg Biryani", 80.0));

        restaurantService.onboardRestaurant("R3", 1, 4.9, menu(
                "Gobi Manchurian", 150.0,
                "Idli", 15.0,
                "Paneer Butter Masala", 175.0,
                "Dosa", 30.0));

        System.out.println("Onboarded: " + restaurantService.getAllRestaurants());

        // ---- Update menus ----
        restaurantService.addMenuItem("R1", "Chicken65", 250.0);
        restaurantService.updateMenuItemPrice("R2", "Paneer Butter Masala", 150.0);
        System.out.println("R1 menu after update: " + restaurantService.getRestaurant("R1").getMenu().keySet());
        System.out.println("R2 'Paneer Butter Masala' new price: "
                + restaurantService.getRestaurant("R2").getMenu().get("Paneer Butter Masala").getPrice());

        // ---- Order 1: Ashwin, 3 Idli + 1 Dosa, lowest cost -> expect R3 (75 < R2's 80) ----
        placeAndPrint(orderService, "Ashwin", items("Idli", 3, "Dosa", 1), lowestCost);

        // ---- Order 2: Harish, same items, lowest cost -> expect R2 (R3 now full) ----
        placeAndPrint(orderService, "Harish", items("Idli", 3, "Dosa", 1), lowestCost);

        // ---- Order 3: Shruthi, 3 Veg Biryani, highest rating -> expect R1 (4.5 > R2's 4.0) ----
        placeAndPrint(orderService, "Shruthi", items("Veg Biryani", 3), highestRating);

        // ---- R3 completes Order 1, freeing its single slot ----
        Order order1 = orderService.getOrder(1);
        orderService.completeOrder(order1.getId());
        System.out.println("R3 marked Order " + order1.getId() + " as COMPLETED -> " + order1);

        // ---- Order 4: Harish, same items again, lowest cost -> expect R3 (free again, cheaper) ----
        placeAndPrint(orderService, "Harish", items("Idli", 3, "Dosa", 1), lowestCost);

        // ---- Order 5: unfulfillable item -> expect rejection ----
        placeAndPrint(orderService, "xyz", items("Paneer Tikka", 1, "Idli", 1), lowestCost);
    }

    private static void placeAndPrint(OrderService orderService, String customer,
                                       Map<String, Integer> items, RestaurantSelectionStrategy strategy) {
        try {
            Order order = orderService.placeOrder(customer, items, strategy);
            System.out.println("Order [" + customer + ", " + items + ", " + strategy.name() + "] -> assigned to "
                    + order.getAssignedRestaurant().getName() + " (orderId=" + order.getId() + ")");
        } catch (NoRestaurantAvailableException e) {
            System.out.println("Order [" + customer + ", " + items + ", " + strategy.name()
                    + "] -> Order can't be fulfilled: " + e.getMessage());
        }
    }

    private static Map<String, Double> menu(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Double) kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Integer> items(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }
}
