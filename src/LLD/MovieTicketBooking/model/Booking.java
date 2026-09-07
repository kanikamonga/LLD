package LLD.MovieTicketBooking.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Immutable booking confirmation generated after successful payment. */
public final class Booking {
    private final String id = UUID.randomUUID().toString();
    private final String sessionId;
    private final String userId;
    private final String showId;
    private final Set<String> seatIds;

    public Booking(String sessionId, String userId, String showId, Set<String> seatIds) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.showId = showId;
        this.seatIds = Collections.unmodifiableSet(new HashSet<>(seatIds));
    }

    public String id() { return id; }
    public String sessionId() { return sessionId; }
    public String userId() { return userId; }
    public String showId() { return showId; }
    public Set<String> seatIds() { return seatIds; }

    @Override
    public String toString() {
        return "Booking{id='" + id + "', show='" + showId + "', seats=" + seatIds + "}";
    }
}
