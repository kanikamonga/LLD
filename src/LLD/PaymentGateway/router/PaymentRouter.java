package LLD.PaymentGateway.router;

import LLD.PaymentGateway.bank.Bank;
import LLD.PaymentGateway.model.PaymentMethod;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Routes each payment method using a configurable weighted distribution.
 * The configured percentages for one method must add up to 100.
 */
public final class PaymentRouter {
    private final Map<String, Bank> banks = new LinkedHashMap<>();
    private final Map<PaymentMethod, List<Route>> routes =
            new EnumMap<>(PaymentMethod.class);
    private final Random random;

    public PaymentRouter() {
        this(new Random());
    }

    public PaymentRouter(Random random) {
        if (random == null) throw new IllegalArgumentException("Random source is required");
        this.random = random;
    }

    public synchronized void registerBank(Bank bank) {
        if (bank == null || bank.id() == null || bank.id().trim().isEmpty()) {
            throw new IllegalArgumentException("Valid bank is required");
        }
        if (banks.putIfAbsent(bank.id(), bank) != null) {
            throw new IllegalArgumentException("Bank already registered: " + bank.id());
        }
    }

    public synchronized void configureDistribution(PaymentMethod method,
                                                    Map<String, Integer> percentages) {
        if (method == null || percentages == null || percentages.isEmpty()) {
            throw new IllegalArgumentException("Method and distribution are required");
        }
        int total = 0;
        List<Route> configured = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : percentages.entrySet()) {
            if (!banks.containsKey(entry.getKey())
                    || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Invalid bank distribution");
            }
            total += entry.getValue();
            configured.add(new Route(entry.getKey(), entry.getValue()));
        }
        if (total != 100) {
            throw new IllegalArgumentException("Distribution must total 100");
        }
        routes.put(method, configured);
    }

    public synchronized Bank route(PaymentMethod method) {
        List<Route> configured = routes.get(method);
        if (configured == null) {
            throw new IllegalStateException("No distribution configured for " + method);
        }
        int selected = random.nextInt(100) + 1;
        int cumulative = 0;
        for (Route route : configured) {
            cumulative += route.percentage;
            if (selected <= cumulative) return banks.get(route.bankId);
        }
        throw new IllegalStateException("Unable to route payment");
    }

    public synchronized Map<PaymentMethod, Map<String, Integer>> distribution() {
        Map<PaymentMethod, Map<String, Integer>> result = new EnumMap<>(PaymentMethod.class);
        for (Map.Entry<PaymentMethod, List<Route>> entry : routes.entrySet()) {
            Map<String, Integer> methodRoutes = new LinkedHashMap<>();
            for (Route route : entry.getValue()) {
                methodRoutes.put(route.bankId, route.percentage);
            }
            result.put(entry.getKey(), methodRoutes);
        }
        return result;
    }

    private static final class Route {
        private final String bankId;
        private final int percentage;
        private Route(String bankId, int percentage) {
            this.bankId = bankId;
            this.percentage = percentage;
        }
    }
}
