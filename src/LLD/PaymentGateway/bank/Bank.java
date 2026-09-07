package LLD.PaymentGateway.bank;

import LLD.PaymentGateway.model.PaymentRequest;
import LLD.PaymentGateway.model.PaymentResult;

/** Bank integration contract used by the traffic router. */
public interface Bank {
    String id();
    PaymentResult process(PaymentRequest request);
}
