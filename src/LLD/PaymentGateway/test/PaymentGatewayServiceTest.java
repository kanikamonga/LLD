package LLD.PaymentGateway.test;

import LLD.PaymentGateway.bank.Bank;
import LLD.PaymentGateway.model.CardDetails;
import LLD.PaymentGateway.model.Client;
import LLD.PaymentGateway.model.PaymentMethod;
import LLD.PaymentGateway.model.PaymentRequest;
import LLD.PaymentGateway.model.PaymentResult;
import LLD.PaymentGateway.router.PaymentRouter;
import LLD.PaymentGateway.service.PaymentGatewayService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Random;

/** Lightweight executable tests for routing, onboarding, and validation behavior. */
public final class PaymentGatewayServiceTest {
    public static void main(String[] args) {
        routesToConfiguredBank();
        rejectsUnknownClient();
        validatesCardDetails();
        rejectsInvalidDistribution();
        System.out.println("PaymentGatewayServiceTest: all tests passed");
    }

    private static void routesToConfiguredBank() {
        PaymentRouter router = new PaymentRouter(new Random(1));
        router.registerBank(new SuccessfulBank("HDFC"));
        router.configureDistribution(PaymentMethod.CARD,
                Collections.singletonMap("HDFC", 100));
        PaymentGatewayService gateway = new PaymentGatewayService(router);
        gateway.onboardClient(new Client("CLIENT-1", "Client"));

        PaymentRequest request = new PaymentRequest(
                "TXN-1", new BigDecimal("10.00"), "INR",
                new CardDetails("4111111111111111", "12/30", "123"));
        PaymentResult result = gateway.makePayment("CLIENT-1", request);

        assertEquals(PaymentResult.Status.SUCCESS, result.status(), "payment status");
        assertEquals("HDFC", result.bankId(), "routed bank");
    }

    private static void rejectsUnknownClient() {
        PaymentRouter router = new PaymentRouter();
        PaymentGatewayService gateway = new PaymentGatewayService(router);
        expectFailure(() -> gateway.makePayment("UNKNOWN", null),
                "unknown client must be rejected");
    }

    private static void validatesCardDetails() {
        expectFailure(() -> new CardDetails("bad", "12/30", "123"),
                "invalid card must be rejected");
    }

    private static void rejectsInvalidDistribution() {
        PaymentRouter router = new PaymentRouter();
        router.registerBank(new SuccessfulBank("HDFC"));
        expectFailure(() -> router.configureDistribution(PaymentMethod.CARD,
                Collections.singletonMap("HDFC", 90)),
                "distribution must total 100");
    }

    private static void expectFailure(Runnable operation, String message) {
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected validation failure.
        }
    }

    private static void assertEquals(Object expected, Object actual, String field) {
        if (!expected.equals(actual)) {
            throw new AssertionError(field + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class SuccessfulBank implements Bank {
        private final String id;
        private SuccessfulBank(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public PaymentResult process(PaymentRequest request) {
            return PaymentResult.success(request.transactionId(), id);
        }
    }

    private PaymentGatewayServiceTest() { }
}
