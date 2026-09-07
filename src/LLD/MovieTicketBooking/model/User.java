package LLD.MovieTicketBooking.model;

/** Registered user who can create a booking session. */
public final class User {
    private final String id;
    private final String name;

    public User(String id, String name) {
        this.id = required(id, "user id");
        this.name = required(name, "user name");
    }

    public String id() { return id; }
    public String name() { return name; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
