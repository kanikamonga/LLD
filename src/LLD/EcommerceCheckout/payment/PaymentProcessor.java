package LLD.EcommerceCheckout.payment;

import LLD.EcommerceCheckout.model.Order;
import LLD.EcommerceCheckout.model.PaymentDetails;
import java.util.ArrayList;
import java.util.List;

/** Selects the payment strategy without coupling checkout to payment providers. */
public final class PaymentProcessor {
    private final List<PaymentStrategy> strategies;

    public PaymentProcessor(List<PaymentStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("At least one payment strategy is required");
        }
        this.strategies = new ArrayList<>(strategies);
    }
    public String authorize(Order order, PaymentDetails details) {
        return strategy(details).authorize(order, details);
    }
    public void capture(String authorizationId, Order order, PaymentDetails details) {
        strategy(details).capture(authorizationId, order);
    }
    public void refund(String authorizationId, Order order, PaymentDetails details) {
        strategy(details).refund(authorizationId, order);
    }
    private PaymentStrategy strategy(PaymentDetails details) {
        for (PaymentStrategy strategy : strategies) {
            if (strategy.supports(details)) return strategy;
        }
        throw new IllegalArgumentException("Unsupported payment method: " + details.method());
    }
}
