package LLD.foodordering;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks eligible restaurants by ascending total cost to fulfill the order
 * (cheapest first). Ties broken by restaurant name for determinism.
 */
public final class LowestCostStrategy implements RestaurantSelectionStrategy {

    @Override
    public List<Restaurant> rank(List<Restaurant> eligibleRestaurants, Map<String, Integer> requestedItems) {
        return eligibleRestaurants.stream()
                .sorted(Comparator
                        .comparingDouble((Restaurant r) -> r.quoteCost(requestedItems))
                        .thenComparing(Restaurant::getName))
                .toList();
    }

    @Override
    public String name() {
        return "Lowest cost";
    }
}
