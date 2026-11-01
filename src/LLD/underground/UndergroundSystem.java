package LLD.underground;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks active passengers and aggregates completed trips by directed route.
 * All operations synchronize on this instance to publish checkout atomically.
 * BigInteger aggregates avoid overflow across an unbounded number of trips.
 */
public final class UndergroundSystem {
    private record CheckIn(String station, int time) {
    }

    // Structured keys avoid collisions from concatenating station names.
    private record Route(String start, String end) {
    }

    private record TripStatistics(BigInteger totalMinutes, BigInteger tripCount) {
        private TripStatistics add(long duration) {
            return new TripStatistics(totalMinutes.add(BigInteger.valueOf(duration)),
                    tripCount.add(BigInteger.ONE));
        }

        private double average() {
            return new BigDecimal(totalMinutes)
                    .divide(new BigDecimal(tripCount), MathContext.DECIMAL128)
                    .doubleValue();
        }
    }

    private final Map<Integer, CheckIn> activeCheckIns = new HashMap<>();
    private final Map<Route, TripStatistics> completedRoutes = new HashMap<>();

    public synchronized void checkIn(int id, String stationName, int time) {
        validateStation(stationName);
        if (activeCheckIns.containsKey(id)) {
            throw new IllegalStateException("Passenger " + id + " is already checked in");
        }
        activeCheckIns.put(id, new CheckIn(stationName, time));
    }

    public synchronized void checkOut(int id, String stationName, int time) {
        validateStation(stationName);
        CheckIn checkIn = activeCheckIns.get(id);
        if (checkIn == null) {
            throw new IllegalStateException("Passenger " + id + " is not checked in");
        }
        // Widen before subtraction: two valid int timestamps can differ by more than MAX_VALUE.
        long duration = (long) time - checkIn.time();
        if (duration <= 0) {
            throw new IllegalArgumentException("Checkout time must be later than check-in time");
        }
        Route route = new Route(checkIn.station(), stationName);
        TripStatistics previous = completedRoutes.getOrDefault(route,
                new TripStatistics(BigInteger.ZERO, BigInteger.ZERO));
        completedRoutes.put(route, previous.add(duration));
        activeCheckIns.remove(id);
    }

    public synchronized double getAverageTime(String startStation, String endStation) {
        validateStation(startStation);
        validateStation(endStation);
        TripStatistics statistics = completedRoutes.get(new Route(startStation, endStation));
        if (statistics == null) {
            throw new IllegalStateException("No completed trips from "
                    + startStation + " to " + endStation);
        }
        return statistics.average();
    }

    private static void validateStation(String station) {
        if (station == null || station.isBlank()) {
            throw new IllegalArgumentException("Station name must not be blank");
        }
    }
}
