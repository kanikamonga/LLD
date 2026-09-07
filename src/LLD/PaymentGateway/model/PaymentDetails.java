package LLD.PaymentGateway.model;

/** Marker contract for method-specific payment credentials. */
public interface PaymentDetails {
    PaymentMethod method();
}
