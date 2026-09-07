package LLD.MovieTicketBooking.exception;

/** Indicates a failed payment attempt or exhausted payment retries. */
public final class PaymentFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PaymentFailedException(String message) { super(message); }
}
