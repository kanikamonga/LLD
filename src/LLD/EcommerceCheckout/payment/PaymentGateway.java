package LLD.EcommerceCheckout.payment;

import LLD.EcommerceCheckout.model.Order;
import LLD.EcommerceCheckout.model.PaymentDetails;

/** Adapter contract for an external payment provider. */
public interface PaymentGateway {
    String authorize(Order order, PaymentDetails details);
    void capture(String authorizationId, Order order);
    void refund(String authorizationId, Order order);
}
