package LLD.underground;

import java.util.Objects;

/** Owns checkout validation, not passenger lookup or statistics storage. */
public final class ActiveTrip {
    private final Station startStation;
    private final int checkInTime;

    public ActiveTrip(Station startStation, int checkInTime) {
        this.startStation = Objects.requireNonNull(startStation, "startStation");
        this.checkInTime = checkInTime;
    }

    public Station getStartStation() {
        return startStation;
    }

    public int getCheckInTime() {
        return checkInTime;
    }

    public CompletedTrip complete(Station endStation, int checkOutTime) {
        // Widen first: valid int timestamps can differ by more than Integer.MAX_VALUE.
        long duration = (long) checkOutTime - checkInTime;
        return new CompletedTrip(new Route(startStation, endStation), duration);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ActiveTrip)) return false;
        ActiveTrip trip = (ActiveTrip) other;
        return checkInTime == trip.checkInTime && startStation.equals(trip.startStation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startStation, checkInTime);
    }

    @Override
    public String toString() {
        return "ActiveTrip[startStation=" + startStation + ", checkInTime=" + checkInTime + "]";
    }
}
