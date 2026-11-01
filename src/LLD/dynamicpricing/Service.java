package LLD.dynamicpricing;

import java.math.BigDecimal;
import java.util.Objects;

/** A service's base price; all amounts in an engine use the same currency. */
public record Service(String id, BigDecimal basePrice) {
    public Service {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Service ID must not be blank");
        }
        Objects.requireNonNull(basePrice, "basePrice");
        if (basePrice.signum() < 0) {
            throw new IllegalArgumentException("Base price must not be negative");
        }
    }
}
