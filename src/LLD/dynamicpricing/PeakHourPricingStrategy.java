package LLD.dynamicpricing;

import java.math.BigDecimal;
import java.util.Objects;

/** Multiplier of 1.25 means a 25% peak-hour surcharge. */
public final class PeakHourPricingStrategy implements PricingStrategy {
    private final BigDecimal multiplier;

    public PeakHourPricingStrategy(BigDecimal multiplier) {
        Objects.requireNonNull(multiplier, "multiplier");
        if (multiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Peak multiplier must be at least 1");
        }
        this.multiplier = multiplier;
    }

    @Override
    public BigDecimal calculatePrice(Service service, PricingContext context) {
        return context.peakHour() ? service.basePrice().multiply(multiplier) : service.basePrice();
    }
}
