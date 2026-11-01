package LLD.dynamicpricing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Plain-main test harness with no external dependencies. */
public final class PricingEngineTest {
    private static final Service SERVICE = new Service("ride", new BigDecimal("100"));
    private static final PricingContext ELIGIBLE = new PricingContext(true, true, true);
    private static final PricingContext INELIGIBLE = new PricingContext(false, false, false);

    public static void main(String[] args) throws Exception {
        PricingEngine engine = new PricingEngine(new StandardPricingStrategy());
        equal("100.00", engine.calculatePrice(SERVICE, ELIGIBLE));
        List<PricingStrategy> strategies = List.of(
                new PeakHourPricingStrategy(new BigDecimal("1.25")),
                new HolidayDiscountStrategy(new BigDecimal("0.20")),
                new MemberRateStrategy(new BigDecimal("0.85")));
        List<String> expected = List.of("125.00", "80.00", "85.00");
        for (int i = 0; i < strategies.size(); i++) {
            engine.setStrategy(strategies.get(i));
            equal(expected.get(i), engine.calculatePrice(SERVICE, ELIGIBLE));
            equal("100.00", engine.calculatePrice(SERVICE, INELIGIBLE));
        }
        engine.setStrategy((service, context) -> new BigDecimal("1.005"));
        equal("1.01", engine.calculatePrice(SERVICE, ELIGIBLE));
        engine.setStrategy(new HolidayDiscountStrategy(BigDecimal.ONE));
        equal("0.00", engine.calculatePrice(SERVICE, ELIGIBLE));
        engine.setStrategy(new HolidayDiscountStrategy(BigDecimal.ZERO));
        equal("100.00", engine.calculatePrice(SERVICE, ELIGIBLE));
        engine.setStrategy(new MemberRateStrategy(BigDecimal.ZERO));
        equal("0.00", engine.calculatePrice(SERVICE, ELIGIBLE));
        engine.setStrategy(new MemberRateStrategy(BigDecimal.ONE));
        equal("100.00", engine.calculatePrice(SERVICE, ELIGIBLE));

        expect(IllegalArgumentException.class, () -> new Service("x", new BigDecimal("-1")));
        expect(IllegalArgumentException.class, () -> new Service(" ", BigDecimal.ONE));
        expect(IllegalArgumentException.class, () -> new PeakHourPricingStrategy(new BigDecimal("0.5")));
        expect(IllegalArgumentException.class, () -> new HolidayDiscountStrategy(new BigDecimal("1.1")));
        expect(IllegalArgumentException.class, () -> new MemberRateStrategy(new BigDecimal("-0.1")));
        expect(NullPointerException.class, () -> engine.setStrategy(null));
        expect(NullPointerException.class, () -> engine.calculatePrice(null, ELIGIBLE));
        expect(NullPointerException.class, () -> engine.calculatePrice(SERVICE, null));
        engine.setStrategy((service, context) -> null);
        expect(IllegalStateException.class, () -> engine.calculatePrice(SERVICE, ELIGIBLE));
        engine.setStrategy((service, context) -> BigDecimal.ONE.negate());
        expect(IllegalStateException.class, () -> engine.calculatePrice(SERVICE, ELIGIBLE));
        engine.setStrategy((service, context) -> {
            throw new UnsupportedOperationException("Provider unavailable");
        });
        expect(UnsupportedOperationException.class, () -> engine.calculatePrice(SERVICE, ELIGIBLE));
        switchesDoNotChangeInFlightCalculation();
        concurrentSwitches();
        System.out.println("All dynamic pricing tests passed");
    }

    private static void switchesDoNotChangeInFlightCalculation() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        PricingEngine engine = new PricingEngine((service, context) -> {
            entered.countDown();
            try {
                if (!resume.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Calculation was not resumed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Calculation interrupted", e);
            }
            return new BigDecimal("123");
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<BigDecimal> pending = pool.submit(() -> engine.calculatePrice(SERVICE, ELIGIBLE));
            if (!entered.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Calculation did not start");
            }
            engine.setStrategy(new StandardPricingStrategy());
            resume.countDown();
            equal("123.00", pending.get(5, TimeUnit.SECONDS));
            equal("100.00", engine.calculatePrice(SERVICE, ELIGIBLE));
        } finally {
            resume.countDown();
            shutdown(pool);
        }
    }

    private static void concurrentSwitches() throws Exception {
        PricingEngine engine = new PricingEngine(new StandardPricingStrategy());
        PricingStrategy standard = new StandardPricingStrategy();
        PricingStrategy peak = new PeakHourPricingStrategy(new BigDecimal("1.25"));
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 1_000; i++) {
                    engine.setStrategy(i % 2 == 0 ? standard : peak);
                }
                return null;
            }));
            for (int reader = 0; reader < 4; reader++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 1_000; i++) {
                        String price = engine.calculatePrice(SERVICE, ELIGIBLE).toPlainString();
                        if (!price.equals("100.00") && !price.equals("125.00")) {
                            throw new AssertionError("Mixed strategy result: " + price);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            shutdown(pool);
        }
    }

    private static void shutdown(ExecutorService pool) throws InterruptedException {
        pool.shutdownNow();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Workers did not stop");
        }
    }

    private static void equal(String expected, BigDecimal actual) {
        if (!expected.equals(actual.toPlainString())) {
            throw new AssertionError("Expected " + expected + ", received " + actual);
        }
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            if (!type.isInstance(e)) {
                throw e;
            }
            return;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
