package LLD.PaymentGateway.model;

/** Card credentials required for a credit/debit card payment. */
public final class CardDetails implements PaymentDetails {
    private final String cardNumber;
    private final String expiryDate;
    private final String cvv;

    public CardDetails(String cardNumber, String expiryDate, String cvv) {
        if (cardNumber == null || !cardNumber.matches("\\d{12,19}")) {
            throw new IllegalArgumentException("Invalid card number");
        }
        if (expiryDate == null || !expiryDate.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw new IllegalArgumentException("Invalid expiry date");
        }
        if (cvv == null || !cvv.matches("\\d{3,4}")) {
            throw new IllegalArgumentException("Invalid CVV");
        }
        this.cardNumber = cardNumber;
        this.expiryDate = expiryDate;
        this.cvv = cvv;
    }

    public String cardNumber() { return cardNumber; }
    public String expiryDate() { return expiryDate; }
    public String cvv() { return cvv; }
    @Override public PaymentMethod method() { return PaymentMethod.CARD; }
}
