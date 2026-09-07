package LLD.PaymentGateway.model;

/** Outcome returned to the client after bank processing. */
public final class PaymentResult {
    public enum Status { SUCCESS, FAILURE }

    private final String transactionId;
    private final String bankId;
    private final Status status;
    private final String message;

    private PaymentResult(String transactionId, String bankId,
                          Status status, String message) {
        this.transactionId = transactionId;
        this.bankId = bankId;
        this.status = status;
        this.message = message;
    }

    public static PaymentResult success(String transactionId, String bankId) {
        return new PaymentResult(transactionId, bankId, Status.SUCCESS, "Payment successful");
    }

    public static PaymentResult failure(String transactionId, String bankId, String message) {
        return new PaymentResult(transactionId, bankId, Status.FAILURE, message);
    }

    public String transactionId() { return transactionId; }
    public String bankId() { return bankId; }
    public Status status() { return status; }
    public String message() { return message; }
}
