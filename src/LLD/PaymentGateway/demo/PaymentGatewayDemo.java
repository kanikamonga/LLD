package LLD.PaymentGateway.demo;

import LLD.PaymentGateway.bank.SimulatedBank;
import LLD.PaymentGateway.model.CardDetails;
import LLD.PaymentGateway.model.Client;
import LLD.PaymentGateway.model.NetBankingDetails;
import LLD.PaymentGateway.model.PaymentMethod;
import LLD.PaymentGateway.model.PaymentRequest;
import LLD.PaymentGateway.model.UpiDetails;
import LLD.PaymentGateway.router.PaymentRouter;
import LLD.PaymentGateway.service.PaymentGatewayService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Runnable demonstration of onboarding, routing, validation, and payments. */
public final class PaymentGatewayDemo {
    public static void main(String[] args) {
        PaymentRouter router = new PaymentRouter(new Random(7));
        router.registerBank(new SimulatedBank("HDFC", new Random(1)));
        router.registerBank(new SimulatedBank("ICICI", new Random(2)));
        router.registerBank(new SimulatedBank("SBI", new Random(3)));

        router.configureDistribution(PaymentMethod.CARD, distribution("HDFC", 100));
        router.configureDistribution(PaymentMethod.NET_BANKING, distribution("ICICI", 100));
        router.configureDistribution(PaymentMethod.UPI, distribution("HDFC", 30, "SBI", 70));

        PaymentGatewayService gateway = new PaymentGatewayService(router);
        gateway.onboardClient(new Client("CLIENT-1", "Shopping App"));
        System.out.println("Distribution: " + gateway.showDistribution());

        System.out.println(gateway.makePayment("CLIENT-1", request(
                "TXN-CARD", new CardDetails("4111111111111111", "12/30", "123"))).message());
        System.out.println(gateway.makePayment("CLIENT-1", request(
                "TXN-UPI", new UpiDetails("customer@upi"))).message());
        System.out.println(gateway.makePayment("CLIENT-1", request(
                "TXN-NB", new NetBankingDetails("customer", "secret"))).message());
    }

    private static PaymentRequest request(String id,
                                          LLD.PaymentGateway.model.PaymentDetails details) {
        return new PaymentRequest(id, new BigDecimal("499.00"), "INR", details);
    }

    private static Map<String, Integer> distribution(String first, int firstPercent) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(first, firstPercent);
        return result;
    }

    private static Map<String, Integer> distribution(String first, int firstPercent,
                                                     String second, int secondPercent) {
        Map<String, Integer> result = distribution(first, firstPercent);
        result.put(second, secondPercent);
        return result;
    }

    private PaymentGatewayDemo() { }
}
