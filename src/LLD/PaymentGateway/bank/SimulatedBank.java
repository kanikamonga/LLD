package LLD.PaymentGateway.bank;

import LLD.PaymentGateway.model.PaymentRequest;
import LLD.PaymentGateway.model.PaymentResult;
import java.util.Random;

/** In-memory bank adapter whose response is randomly successful or failed. */
public final class SimulatedBank implements Bank {
    private final String id;
    private final Random random;

    public SimulatedBank(String id) {
        this(id, new Random());
    }

    public SimulatedBank(String id, Random random) {
        if (id == null || id.trim().isEmpty() || random == null) {
            throw new IllegalArgumentException("Bank id and random source are required");
        }
        this.id = id;
        this.random = random;
    }

    @Override
    public String id() { return id; }

    @Override
    public PaymentResult process(PaymentRequest request) {
        return random.nextBoolean()
                ? PaymentResult.success(request.transactionId(), id)
                : PaymentResult.failure(request.transactionId(), id, "Bank declined payment");
    }
}
