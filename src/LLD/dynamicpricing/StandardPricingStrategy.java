package LLD.dynamicpricing;

import java.math.BigDecimal;

public final class StandardPricingStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculatePrice(Service service, PricingContext context) {
        return service.basePrice();
    }
}
