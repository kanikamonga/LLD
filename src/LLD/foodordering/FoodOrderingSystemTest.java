package LLD.foodordering;

import LLD.foodordering.exceptions.InvalidOrderStateException;
import LLD.foodordering.exceptions.MenuItemNotFoundException;
import LLD.foodordering.exceptions.NoRestaurantAvailableException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight, dependency-free test harness (this project has no JUnit/build
 * tool wired up - see the existing {@code SingletonTest} for the same
 * plain-main convention). Each test method throws AssertionError on failure;
 * main() runs them all and reports a summary.
 */
public final class FoodOrderingSystemTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        run("lowestCostStrategyPicksCheapestEligibleRestaurant", FoodOrderingSystemTest::lowestCostStrategyPicksCheapestEligibleRestaurant);
        run("highestRatingStrategyPicksBestRatedEligibleRestaurant", FoodOrderingSystemTest::highestRatingStrategyPicksBestRatedEligibleRestaurant);
        run("orderRejectedWhenNoRestaurantCoversAllItems", FoodOrderingSystemTest::orderRejectedWhenNoRestaurantCoversAllItems);
        run("capacityLimitEnforced_thenFreedOnCompletion", FoodOrderingSystemTest::capacityLimitEnforced_thenFreedOnCompletion);
        run("cannotCompleteAnOrderThatIsNotAccepted", FoodOrderingSystemTest::cannotCompleteAnOrderThatIsNotAccepted);
        run("menuItemsCanBeAddedAndPriceUpdated_butNotRemoved", FoodOrderingSystemTest::menuItemsCanBeAddedAndPriceUpdated_butNotRemoved);
        run("updatingPriceOfNonExistentItemThrows", FoodOrderingSystemTest::updatingPriceOfNonExistentItemThrows);
        run("concurrentOrdersRespectSingleSlotCapacity", FoodOrderingSystemTest::concurrentOrdersRespectSingleSlotCapacity);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
            System.out.println("PASS - " + name);
            passed++;
        } catch (Throwable t) {
            System.out.println("FAIL - " + name + " : " + t);
            failed++;
        }
    }

    // ---------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------

    private static RestaurantService newRestaurantService() {
        return new RestaurantService();
    }

    private static Map<String, Double> menu(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Double) kv[i + 1]);
        return m;
    }

    private static Map<String, Integer> items(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + " - expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    static void lowestCostStrategyPicksCheapestEligibleRestaurant() {
        RestaurantService rs = newRestaurantService();
        OrderService os = new OrderService(rs);
        rs.onboardRestaurant("R2", 5, 4.0, menu("Idli", 10.0, "Dosa", 50.0));
        rs.onboardRestaurant("R3", 1, 4.9, menu("Idli", 15.0, "Dosa", 30.0));

        // R3 total = 3*15 + 30 = 75, R2 total = 3*10 + 50 = 80 -> R3 should win
        Order order = os.placeOrder("Ashwin", items("Idli", 3, "Dosa", 1), new LowestCostStrategy());
        assertEquals("R3", order.getAssignedRestaurant().getName(), "cheapest restaurant should be selected");
        assertEquals(OrderStatus.ACCEPTED, order.getStatus(), "order should be ACCEPTED");
    }

    static void highestRatingStrategyPicksBestRatedEligibleRestaurant() {
        RestaurantService rs = newRestaurantService();
        OrderService os = new OrderService(rs);
        rs.onboardRestaurant("R1", 5, 4.5, menu("Veg Biryani", 100.0));
        rs.onboardRestaurant("R2", 5, 4.0, menu("Veg Biryani", 80.0));

        Order order = os.placeOrder("Shruthi", items("Veg Biryani", 3), new HighestRatingStrategy());
        assertEquals("R1", order.getAssignedRestaurant().getName(), "higher rated restaurant should be selected");
    }

    static void orderRejectedWhenNoRestaurantCoversAllItems() {
        RestaurantService rs = newRestaurantService();
        OrderService os = new OrderService(rs);
        rs.onboardRestaurant("R2", 5, 4.0, menu("Idli", 10.0));

        try {
            os.placeOrder("xyz", items("Paneer Tikka", 1, "Idli", 1), new LowestCostStrategy());
            throw new AssertionError("expected NoRestaurantAvailableException");
        } catch (NoRestaurantAvailableException expected) {
            // good
        }
    }

    static void capacityLimitEnforced_thenFreedOnCompletion() {
        RestaurantService rs = newRestaurantService();
        OrderService os = new OrderService(rs);
        rs.onboardRestaurant("R3", 1, 4.9, menu("Idli", 15.0, "Dosa", 30.0));
        rs.onboardRestaurant("R2", 5, 4.0, menu("Idli", 10.0, "Dosa", 50.0));

        Order first = os.placeOrder("Ashwin", items("Idli", 3, "Dosa", 1), new LowestCostStrategy());
        assertEquals("R3", first.getAssignedRestaurant().getName(), "first order should go to cheaper R3");

        // R3 is now full (max=1); second identical order must fall back to R2
        Order second = os.placeOrder("Harish", items("Idli", 3, "Dosa", 1), new LowestCostStrategy());
        assertEquals("R2", second.getAssignedRestaurant().getName(), "R3 should be at capacity, falling back to R2");

        os.completeOrder(first.getId());
        assertEquals(OrderStatus.COMPLETED, first.getStatus(), "completed order should be COMPLETED");
        assertEquals(0, rs.getRestaurant("R3").getCurrentActiveOrders(), "R3 capacity should be freed after completion");

        // Now R3 is free again and cheaper -> third identical order should go back to R3
        Order third = os.placeOrder("Harish", items("Idli", 3, "Dosa", 1), new LowestCostStrategy());
        assertEquals("R3", third.getAssignedRestaurant().getName(), "R3 should be available again after completion");
    }

    static void cannotCompleteAnOrderThatIsNotAccepted() {
        RestaurantService rs = newRestaurantService();
        OrderService os = new OrderService(rs);
        rs.onboardRestaurant("R2", 5, 4.0, menu("Idli", 10.0));

        Order order = os.placeOrder("Ashwin", items("Idli", 1), new LowestCostStrategy());
        os.completeOrder(order.getId()); // first completion succeeds

        try {
            os.completeOrder(order.getId()); // already COMPLETED -> invalid
            throw new AssertionError("expected InvalidOrderStateException on double-complete");
        } catch (InvalidOrderStateException expected) {
            // good
        }
    }

    static void menuItemsCanBeAddedAndPriceUpdated_butNotRemoved() {
        RestaurantService rs = newRestaurantService();
        rs.onboardRestaurant("R1", 5, 4.5, menu("Veg Biryani", 100.0));

        rs.addMenuItem("R1", "Chicken65", 250.0);
        assertTrue(rs.getRestaurant("R1").getMenu().containsKey("Chicken65"), "new item should be added");

        rs.updateMenuItemPrice("R1", "Veg Biryani", 120.0);
        assertEquals(120.0, rs.getRestaurant("R1").getMenu().get("Veg Biryani").getPrice(), "price should be updated");

        assertEquals(2, rs.getRestaurant("R1").getMenu().size(), "menu should only grow, never shrink");
    }

    static void updatingPriceOfNonExistentItemThrows() {
        RestaurantService rs = newRestaurantService();
        rs.onboardRestaurant("R1", 5, 4.5, menu("Veg Biryani", 100.0));
        try {
            rs.updateMenuItemPrice("R1", "Nonexistent", 10.0);
            throw new AssertionError("expected MenuItemNotFoundException");
        } catch (MenuItemNotFoundException expected) {
            // good
        }
    }

    /**
     * Concurrency test: a restaurant with capacity=1 receives many concurrent
     * order placements; exactly one should be ACCEPTED and the rest REJECTED
     * (via NoRestaurantAvailableException), proving tryReserveCapacity() is
     * race-free.
     */
    static void concurrentOrdersRespectSingleSlotCapacity() {
        RestaurantService rs = newRestaurantService();
        OrderService os = new OrderService(rs);
        rs.onboardRestaurant("R3", 1, 4.9, menu("Idli", 15.0));

        final int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    os.placeOrder("customer-" + idx, items("Idli", 1), new LowestCostStrategy());
                    accepted.incrementAndGet();
                } catch (NoRestaurantAvailableException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // release all threads at once to maximize contention
        try {
            boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
            assertTrue(finished, "all threads should finish within timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, accepted.get(), "exactly one order should be accepted given capacity=1");
        assertEquals(threadCount - 1, rejected.get(), "all other orders should be rejected");
        assertEquals(1, rs.getRestaurant("R3").getCurrentActiveOrders(), "restaurant should show exactly 1 active order");
    }
}
