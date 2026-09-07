package LLD.PaymentGateway.service;

import LLD.PaymentGateway.bank.Bank;
import LLD.PaymentGateway.model.Client;
import LLD.PaymentGateway.model.PaymentRequest;
import LLD.PaymentGateway.model.PaymentResult;
import LLD.PaymentGateway.router.PaymentRouter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Entry point for client onboarding, distribution display, and payment processing. */
public final class PaymentGatewayService {
    private final Map<String, Client> clients = new LinkedHashMap<>();
    private final PaymentRouter router;

    public PaymentGatewayService(PaymentRouter router) {
        if (router == null) throw new IllegalArgumentException("Router is required");
        this.router = router;
    }

    public synchronized void onboardClient(Client client) {
        if (client == null) throw new IllegalArgumentException("Client is required");
        if (clients.putIfAbsent(client.id(), client) != null) {
            throw new IllegalArgumentException("Client already onboarded: " + client.id());
        }
    }

    public synchronized PaymentResult makePayment(String clientId, PaymentRequest request) {
        if (!clients.containsKey(clientId)) {
            throw new IllegalArgumentException("Client is not onboarded: " + clientId);
        }
        Bank bank = router.route(request.details().method());
        return bank.process(request);
    }

    public synchronized Map<?, ?> showDistribution() {
        return Collections.unmodifiableMap(router.distribution());
    }
}
