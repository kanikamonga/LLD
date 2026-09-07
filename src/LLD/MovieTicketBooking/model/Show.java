package LLD.MovieTicketBooking.model;

import LLD.MovieTicketBooking.exception.SeatsUnavailableException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns seat state for one movie show and atomically changes that state. */
public final class Show {
    private final String id;
    private final Movie movie;
    private final Screen screen;
    private final LocalDateTime startTime;
    private final Map<String, SeatStatus> seatStatuses = new HashMap<>();
    private final Map<String, String> seatHolders = new HashMap<>();
    private final Object seatLock = new Object();

    public Show(String id, Movie movie, Screen screen, LocalDateTime startTime) {
        this.id = required(id, "show id");
        if (movie == null || screen == null || startTime == null) {
            throw new IllegalArgumentException("Movie, screen, and start time are required");
        }
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        for (Seat seat : screen.seats()) {
            seatStatuses.put(seat.id(), SeatStatus.AVAILABLE);
        }
    }

    public String id() { return id; }
    public Movie movie() { return movie; }
    public Screen screen() { return screen; }
    public LocalDateTime startTime() { return startTime; }

    /** Returns a consistent snapshot; callers must still reserve seats explicitly. */
    public List<Seat> availableSeats() {
        synchronized (seatLock) {
            List<Seat> result = new ArrayList<>();
            for (Seat seat : screen.seats()) {
                if (seatStatuses.get(seat.id()) == SeatStatus.AVAILABLE) {
                    result.add(seat);
                }
            }
            return result;
        }
    }

    /** Atomically validates and holds the complete requested group. */
    public void holdSeats(String sessionId, Set<String> requestedSeatIds) {
        synchronized (seatLock) {
            for (String seatId : requestedSeatIds) {
                if (seatStatuses.get(seatId) != SeatStatus.AVAILABLE) {
                    throw new SeatsUnavailableException(
                            "One or more of the selected seats are not available at this moment");
                }
            }
            for (String seatId : requestedSeatIds) {
                seatStatuses.put(seatId, SeatStatus.HELD);
                seatHolders.put(seatId, sessionId);
            }
        }
    }

    /** Releases only seats currently owned by the supplied session. */
    public void releaseSeats(String sessionId, Set<String> seatIds) {
        synchronized (seatLock) {
            for (String seatId : seatIds) {
                if (sessionId.equals(seatHolders.get(seatId))) {
                    seatStatuses.put(seatId, SeatStatus.AVAILABLE);
                    seatHolders.remove(seatId);
                }
            }
        }
    }

    /** Converts a session's held seats into permanent bookings atomically. */
    public void bookSeats(String sessionId, Set<String> seatIds) {
        synchronized (seatLock) {
            for (String seatId : seatIds) {
                if (!sessionId.equals(seatHolders.get(seatId))
                        || seatStatuses.get(seatId) != SeatStatus.HELD) {
                    throw new IllegalStateException("Seats are no longer held by this session");
                }
            }
            for (String seatId : seatIds) {
                seatStatuses.put(seatId, SeatStatus.BOOKED);
                seatHolders.remove(seatId);
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
