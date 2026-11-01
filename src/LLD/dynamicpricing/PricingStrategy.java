package LLD.dynamicpricing;

import java.math.BigDecimal;

/**
 * Computes an unrounded, nonnegative price from a service and eligibility context.
 * Implementations shared by the engine must be thread-safe; built-in strategies
 * are immutable. Failures propagate to the caller, never fall back silently.
 */
@FunctionalInterface
public interface PricingStrategy {
    BigDecimal calculatePrice(Service service, PricingContext context);
}
