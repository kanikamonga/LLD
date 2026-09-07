package LLD.EcommerceCheckout.model;

/** Token identifying a digital wallet account. */
public final class WalletDetails implements PaymentDetails {
    private final String walletToken;

    public WalletDetails(String walletToken) {
        if (walletToken == null || walletToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet token cannot be blank");
        }
        this.walletToken = walletToken;
    }

    @Override public PaymentMethod method() { return PaymentMethod.DIGITAL_WALLET; }
    public String walletToken() { return walletToken; }
}
