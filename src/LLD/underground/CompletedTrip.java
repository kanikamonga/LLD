package LLD.underground;

import java.util.Objects;

/** Immutable completion result; created during checkout, not retained as history. */
public final class CompletedTrip {
    private final Route route;
    private final long durationMinutes;

    public CompletedTrip(Route route, long durationMinutes) {
        this.route = Objects.requireNonNull(route, "route");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Checkout time must be later than check-in time");
        }
        this.durationMinutes = durationMinutes;
    }

    public Route getRoute() {
        return route;
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CompletedTrip)) return false;
        CompletedTrip trip = (CompletedTrip) other;
        return durationMinutes == trip.durationMinutes && route.equals(trip.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(route, durationMinutes);
    }

    @Override
    public String toString() {
        return "CompletedTrip[route=" + route + ", durationMinutes=" + durationMinutes + "]";
    }
}
