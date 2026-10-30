package LLD.coffeemachine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable recipes and initial inventory; quantities use a consistent unit per ingredient. */
public final class MachineConfig {
    private final int outlets;
    private final Map<String, Long> inventory;
    private final Map<String, Map<String, Long>> beverages;

    public MachineConfig(int outlets, Map<String, Long> inventory,
                         Map<String, Map<String, Long>> beverages) {
        if (outlets <= 0) throw new IllegalArgumentException("outlets must be positive");
        this.outlets = outlets;
        this.inventory = copyQuantities(inventory, false);
        Objects.requireNonNull(beverages, "beverages");
        if (beverages.isEmpty()) throw new IllegalArgumentException("beverages must not be empty");
        Map<String, Map<String, Long>> copy = new LinkedHashMap<>();
        beverages.forEach((name, recipe) -> {
            requireName(name);
            if (Objects.requireNonNull(recipe, "recipe").isEmpty())
                throw new IllegalArgumentException("recipe must not be empty: " + name);
            copy.put(name, copyQuantities(recipe, true));
        });
        this.beverages = Collections.unmodifiableMap(copy);
    }

    static void requireName(String name) {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("name must not be blank");
    }

    private static Map<String, Long> copyQuantities(Map<String, Long> source, boolean positive) {
        Objects.requireNonNull(source, "quantities");
        Map<String, Long> copy = new LinkedHashMap<>();
        source.forEach((name, quantity) -> {
            requireName(name);
            if (quantity == null || quantity < 0 || (positive && quantity == 0))
                throw new IllegalArgumentException("invalid quantity for " + name);
            copy.put(name, quantity);
        });
        return Collections.unmodifiableMap(copy);
    }

    public int outlets() { return outlets; }
    public Map<String, Long> inventory() { return inventory; }
    public Map<String, Map<String, Long>> beverages() { return beverages; }
}
