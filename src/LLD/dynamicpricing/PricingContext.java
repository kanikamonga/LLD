package LLD.dynamicpricing;

/**
 * Eligibility supplied by the caller, not inferred from the system clock.
 * A scheduling/calendar layer determines peak hours and holidays for the
 * service location; a customer layer determines membership.
 */
public record PricingContext(boolean peakHour, boolean holiday, boolean member) {
}
