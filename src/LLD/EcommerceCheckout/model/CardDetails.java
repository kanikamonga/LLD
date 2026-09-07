package LLD.EcommerceCheckout.model;

/** Tokenized card data; raw PAN/CVV should not be stored in production. */
public final class CardDetails implements PaymentDetails {
    private final String token;
    private final PaymentMethod method;

    public CardDetails(PaymentMethod method, String token) {
        if (method != PaymentMethod.CREDIT_CARD && method != PaymentMethod.DEBIT_CARD) {
            throw new IllegalArgumentException("Card method required");
        }
        this.method = method;
        this.token = required(token, "card token");
    }

    @Override public PaymentMethod method() { return method; }
    public String token() { return token; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
