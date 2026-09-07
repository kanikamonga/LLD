package LLD.MovieTicketBooking.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Tracks temporary seat ownership and payment state for one user workflow. */
public final class BookingSession {
    private final String id;
    private final User user;
    private final Show show;
    private final Instant expiresAt;
    private final Set<String> selectedSeatIds = new HashSet<>();
    private boolean closed;
    private int paymentAttempts;

    public BookingSession(String id, User user, Show show, Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.show = show;
        this.expiresAt = expiresAt;
    }

    public String id() { return id; }
    public User user() { return user; }
    public Show show() { return show; }
    public Instant expiresAt() { return expiresAt; }
    public Set<String> selectedSeatIds() {
        return Collections.unmodifiableSet(new HashSet<>(selectedSeatIds));
    }
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
    public boolean isClosed() { return closed; }
    public int paymentAttempts() { return paymentAttempts; }
    public int incrementPaymentAttempts() { return ++paymentAttempts; }
    public void addSelectedSeats(Set<String> seatIds) { selectedSeatIds.addAll(seatIds); }
    public void close() { closed = true; }
}
