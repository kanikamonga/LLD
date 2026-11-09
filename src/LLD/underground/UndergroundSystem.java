package LLD.underground;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks active passengers and aggregates completed trips by directed route.
 * All operations synchronize on this instance to publish checkout atomically.
 * Domain objects own validation and arithmetic; this facade owns lifecycle coordination.
 */
public final class UndergroundSystem {
    private final Map<Integer, ActiveTrip> activeCheckIns = new HashMap<>();
    private final Map<Route, RouteStatistics> completedRoutes = new HashMap<>();

    public synchronized void checkIn(int id, String stationName, int time) {
        Station station = new Station(stationName);
        if (activeCheckIns.containsKey(id)) {
            throw new IllegalStateException("Passenger " + id + " is already checked in");
        }
        activeCheckIns.put(id, new ActiveTrip(station, time));
    }

    public synchronized void checkOut(int id, String stationName, int time) {
        Station station = new Station(stationName);
        ActiveTrip checkIn = activeCheckIns.get(id);
        if (checkIn == null) {
            throw new IllegalStateException("Passenger " + id + " is not checked in");
        }
        CompletedTrip trip = checkIn.complete(station, time);
        RouteStatistics previous = completedRoutes.getOrDefault(trip.getRoute(), new RouteStatistics());
        completedRoutes.put(trip.getRoute(), previous.addDuration(trip.getDurationMinutes()));
        activeCheckIns.remove(id);
    }

    public synchronized double getAverageTime(String startStation, String endStation) {
        Route route = new Route(new Station(startStation), new Station(endStation));
        RouteStatistics statistics = completedRoutes.get(route);
        if (statistics == null) {
            throw new IllegalStateException("No completed trips from "
                    + startStation + " to " + endStation);
        }
        return statistics.average();
    }

}
