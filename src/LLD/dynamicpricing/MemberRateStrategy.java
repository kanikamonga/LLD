package LLD.dynamicpricing;

import java.math.BigDecimal;
import java.util.Objects;

/** Members pay the given fraction of base price: 0.85 means 85% of base price. */
public final class MemberRateStrategy implements PricingStrategy {
    private final BigDecimal rate;

    public MemberRateStrategy(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate");
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Member rate must be between 0 and 1");
        }
        this.rate = rate;
    }

    @Override
    public BigDecimal calculatePrice(Service service, PricingContext context) {
        return context.member() ? service.basePrice().multiply(rate) : service.basePrice();
    }
}
