package LLD.dynamicpricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Strategy context: delegates business rules and owns common result validation
 * and rounding. A strategy switch affects subsequent reads, not in-flight calculations.
 */
public final class PricingEngine {
    private volatile PricingStrategy strategy;

    public PricingEngine(PricingStrategy strategy) {
        setStrategy(strategy);
    }

    public void setStrategy(PricingStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public BigDecimal calculatePrice(Service service, PricingContext context) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(context, "context");
        PricingStrategy selected = strategy; // One snapshot: never mix rules during a concurrent switch.
        BigDecimal result = selected.calculatePrice(service, context);
        if (result == null || result.signum() < 0) {
            throw new IllegalStateException("Pricing strategy returned a null or negative price");
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }
}
