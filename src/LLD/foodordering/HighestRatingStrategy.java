package LLD.foodordering;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks eligible restaurants by descending rating (best-rated first).
 * Ties broken by restaurant name for determinism.
 */
public final class HighestRatingStrategy implements RestaurantSelectionStrategy {

    @Override
    public List<Restaurant> rank(List<Restaurant> eligibleRestaurants, Map<String, Integer> requestedItems) {
        return eligibleRestaurants.stream()
                .sorted(Comparator
                        .comparingDouble(Restaurant::getRating).reversed()
                        .thenComparing(Restaurant::getName))
                .toList();
    }

    @Override
    public String name() {
        return "Highest rating";
    }
}
