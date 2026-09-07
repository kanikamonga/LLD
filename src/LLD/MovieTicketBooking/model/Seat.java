package LLD.MovieTicketBooking.model;

/** Immutable physical seat belonging to a screen. */
public final class Seat {
    private final String id;
    private final String label;

    public Seat(String id, String label) {
        this.id = required(id, "seat id");
        this.label = required(label, "seat label");
    }

    public String id() { return id; }
    public String label() { return label; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
