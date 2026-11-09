package LLD.underground;

import java.util.Objects;

/** Directed value object: A -> B is different from B -> A. */
public final class Route {
    private final Station start;
    private final Station end;

    public Route(Station start, Station end) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
    }

    public Station getStart() {
        return start;
    }

    public Station getEnd() {
        return end;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Route)) return false;
        Route route = (Route) other;
        return start.equals(route.start) && end.equals(route.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "Route[start=" + start + ", end=" + end + "]";
    }
}
