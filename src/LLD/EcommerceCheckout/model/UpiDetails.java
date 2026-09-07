package LLD.EcommerceCheckout.model;

/** Virtual payment address used by the UPI strategy. */
public final class UpiDetails implements PaymentDetails {
    private final String vpa;

    public UpiDetails(String vpa) {
        if (vpa == null || !vpa.matches("[^@\\s]+@[^@\\s]+")) {
            throw new IllegalArgumentException("Invalid UPI VPA");
        }
        this.vpa = vpa;
    }

    @Override public PaymentMethod method() { return PaymentMethod.UPI; }
    public String vpa() { return vpa; }
}
