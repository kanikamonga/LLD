package LLD.MovieTicketBooking.exception;

/** Indicates that a booking session can no longer be used. */
public final class SessionExpiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public SessionExpiredException(String message) { super(message); }
}
