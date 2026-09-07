package LLD.MovieTicketBooking.model;

import java.time.Duration;

/** Immutable movie metadata used by a show. */
public final class Movie {
    private final String id;
    private final String title;
    private final Duration duration;

    public Movie(String id, String title, Duration duration) {
        this.id = required(id, "movie id");
        this.title = required(title, "movie title");
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Movie duration must be positive");
        }
        this.duration = duration;
    }

    public String id() { return id; }
    public String title() { return title; }
    public Duration duration() { return duration; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
