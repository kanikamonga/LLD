package LLD.EcommerceCheckout.payment;

import LLD.EcommerceCheckout.model.Order;
import LLD.EcommerceCheckout.model.PaymentDetails;

/** Strategy for selecting and invoking a gateway for one payment method. */
public interface PaymentStrategy {
    boolean supports(PaymentDetails details);
    String authorize(Order order, PaymentDetails details);
    void capture(String authorizationId, Order order);
    void refund(String authorizationId, Order order);
}
