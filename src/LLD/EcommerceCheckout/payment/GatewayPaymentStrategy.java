package LLD.EcommerceCheckout.payment;

import LLD.EcommerceCheckout.model.Order;
import LLD.EcommerceCheckout.model.PaymentDetails;
import LLD.EcommerceCheckout.model.PaymentMethod;

/** Delegates one payment method to its configured gateway adapter. */
public final class GatewayPaymentStrategy implements PaymentStrategy {
    private final PaymentMethod method;
    private final PaymentGateway gateway;

    public GatewayPaymentStrategy(PaymentMethod method, PaymentGateway gateway) {
        this.method = method;
        this.gateway = gateway;
    }
    @Override public boolean supports(PaymentDetails details) {
        return details.method() == method;
    }
    @Override public String authorize(Order order, PaymentDetails details) {
        return gateway.authorize(order, details);
    }
    @Override public void capture(String authorizationId, Order order) {
        gateway.capture(authorizationId, order);
    }
    @Override public void refund(String authorizationId, Order order) {
        gateway.refund(authorizationId, order);
    }
}
