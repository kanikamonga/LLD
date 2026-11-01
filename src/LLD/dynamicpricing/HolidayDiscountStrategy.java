package LLD.dynamicpricing;

import java.math.BigDecimal;
import java.util.Objects;

/** Discount is a fraction: 0.20 means 20% off. */
public final class HolidayDiscountStrategy implements PricingStrategy {
    private final BigDecimal remainingFraction;

    public HolidayDiscountStrategy(BigDecimal discount) {
        Objects.requireNonNull(discount, "discount");
        if (discount.signum() < 0 || discount.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Discount must be between 0 and 1");
        }
        remainingFraction = BigDecimal.ONE.subtract(discount);
    }

    @Override
    public BigDecimal calculatePrice(Service service, PricingContext context) {
        return context.holiday() ? service.basePrice().multiply(remainingFraction) : service.basePrice();
    }
}
