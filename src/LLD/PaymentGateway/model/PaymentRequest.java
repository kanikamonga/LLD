package LLD.PaymentGateway.model;

import java.math.BigDecimal;

/** Immutable payment command received from an onboarded client. */
public final class PaymentRequest {
    private final String transactionId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentDetails details;

    public PaymentRequest(String transactionId, BigDecimal amount,
                          String currency, PaymentDetails details) {
        this.transactionId = required(transactionId, "transaction id");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        this.amount = amount;
        this.currency = required(currency, "currency");
        if (details == null) throw new IllegalArgumentException("Payment details required");
        this.details = details;
    }

    public String transactionId() { return transactionId; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public PaymentDetails details() { return details; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
