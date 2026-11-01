package LLD.dynamicpricing;

import java.math.BigDecimal;

public final class PricingDemo {
    public static void main(String[] args) {
        Service ride = new Service("airport-transfer", new BigDecimal("100.00"));
        PricingContext context = new PricingContext(true, true, true);
        PricingEngine engine = new PricingEngine(new StandardPricingStrategy());
        System.out.println("Standard: " + engine.calculatePrice(ride, context));

        engine.setStrategy(new PeakHourPricingStrategy(new BigDecimal("1.25")));
        System.out.println("Peak hour: " + engine.calculatePrice(ride, context));

        engine.setStrategy(new HolidayDiscountStrategy(new BigDecimal("0.20")));
        System.out.println("Holiday: " + engine.calculatePrice(ride, context));

        engine.setStrategy(new MemberRateStrategy(new BigDecimal("0.85")));
        System.out.println("Member: " + engine.calculatePrice(ride, context));

        // New behavior supplied without any modification to PricingEngine.
        engine.setStrategy((service, eligibility) -> service.basePrice().add(new BigDecimal("5")));
        System.out.println("Custom fixed surcharge: " + engine.calculatePrice(ride, context));
    }
}
