package LLD.PaymentGateway.model;

/** Bank username and password required for net banking. */
public final class NetBankingDetails implements PaymentDetails {
    private final String username;
    private final String password;

    public NetBankingDetails(String username, String password) {
        this.username = required(username, "username");
        this.password = required(password, "password");
    }

    public String username() { return username; }
    public String password() { return password; }
    @Override public PaymentMethod method() { return PaymentMethod.NET_BANKING; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
