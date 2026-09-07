package LLD.PaymentGateway.model;

/** VPA details required for a UPI payment. */
public final class UpiDetails implements PaymentDetails {
    private final String vpa;

    public UpiDetails(String vpa) {
        if (vpa == null || !vpa.matches("[^@\\s]+@[^@\\s]+")) {
            throw new IllegalArgumentException("Invalid VPA");
        }
        this.vpa = vpa;
    }

    public String vpa() { return vpa; }
    @Override public PaymentMethod method() { return PaymentMethod.UPI; }
}
