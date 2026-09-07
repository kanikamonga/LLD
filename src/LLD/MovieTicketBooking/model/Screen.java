package LLD.MovieTicketBooking.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Defines the fixed seat arrangement for a theatre screen. */
public final class Screen {
    private final String id;
    private final List<Seat> seats;

    public Screen(String id, List<Seat> seats) {
        this.id = required(id, "screen id");
        if (seats == null || seats.isEmpty()
                || new HashSet<>(seats.stream().map(Seat::id).collect(
                java.util.stream.Collectors.toList())).size() != seats.size()) {
            throw new IllegalArgumentException("Screen must contain unique seats");
        }
        this.seats = Collections.unmodifiableList(new ArrayList<>(seats));
    }

    public String id() { return id; }
    public List<Seat> seats() { return seats; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
