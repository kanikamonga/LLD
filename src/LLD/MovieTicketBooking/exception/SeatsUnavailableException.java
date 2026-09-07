package LLD.MovieTicketBooking.exception;

/** Indicates that at least one requested seat is no longer available. */
public final class SeatsUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public SeatsUnavailableException(String message) { super(message); }
}
