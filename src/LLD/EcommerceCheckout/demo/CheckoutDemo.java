package LLD.EcommerceCheckout.demo;

import LLD.EcommerceCheckout.inventory.InventoryService;
import LLD.EcommerceCheckout.model.CardDetails;
import LLD.EcommerceCheckout.model.CartItem;
import LLD.EcommerceCheckout.model.CheckoutRequest;
import LLD.EcommerceCheckout.model.Order;
import LLD.EcommerceCheckout.model.PaymentDetails;
import LLD.EcommerceCheckout.model.PaymentMethod;
import LLD.EcommerceCheckout.payment.GatewayPaymentStrategy;
import LLD.EcommerceCheckout.payment.PaymentGateway;
import LLD.EcommerceCheckout.payment.PaymentProcessor;
import LLD.EcommerceCheckout.payment.PaymentStrategy;
import LLD.EcommerceCheckout.service.CheckoutService;
import java.math.BigDecimal;
import java.util.Arrays;

/** Demonstrates a successful checkout using the card payment strategy. */
public final class CheckoutDemo {
    public static void main(String[] args) {
        InventoryService inventory = new InventoryService();
        inventory.addStock("SKU-1", 3);
        PaymentGateway gateway = new DemoGateway();
        PaymentStrategy card = new GatewayPaymentStrategy(PaymentMethod.CREDIT_CARD, gateway);
        CheckoutService checkout = new CheckoutService(
                inventory, new PaymentProcessor(Arrays.asList(card)));

        CheckoutRequest request = new CheckoutRequest(
                "USER-1", "IDEMPOTENCY-1",
                Arrays.asList(new CartItem("SKU-1", 2, new BigDecimal("49.99"))),
                new CardDetails(PaymentMethod.CREDIT_CARD, "card-token"));
        Order order = checkout.checkout(request);
        System.out.println("Checkout succeeded: " + order.id()
                + ", status=" + order.status());
    }

    private static final class DemoGateway implements PaymentGateway {
        @Override public String authorize(Order order, PaymentDetails details) {
            return "AUTH-" + order.id();
        }
        @Override public void capture(String authorizationId, Order order) { }
        @Override public void refund(String authorizationId, Order order) { }
    }

    private CheckoutDemo() { }
}
