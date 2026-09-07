package DSA.Google;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/*
 * Problem:
 * Given train schedules containing a departure city, arrival city, departure
 * time, arrival time, and duration, find the sequence of trains that takes a
 * passenger from source to destination at the earliest possible arrival time.
 *
 * A passenger may board a train only when its departure time is at least the
 * time at which the passenger reaches that city. Waiting at a city is allowed.
 *
 * Approach:
 * This is a time-dependent shortest-path problem. For every city, store the
 * earliest time at which it can be reached. Use Dijkstra's algorithm:
 *
 * 1. Start at source with the requested start time.
 * 2. From the city removed from the priority queue, consider every outgoing
 *    train whose departure time is not earlier than the current arrival time.
 * 3. If that train reaches the next city earlier than its known arrival time,
 *    update the time and remember the train as its predecessor.
 * 4. Follow predecessors backward from destination to reconstruct the route.
 *
 * Time Complexity: O((V + E) log V), where V is the number of cities and E is
 * the number of trains.
 * Space Complexity: O(V + E).
 */
public class EarliestTrainRoute {

    public static class Train {
        public final String departureCity;
        public final String arrivalCity;
        public final int startTime;
        public final int endTime;
        public final int duration;

        public Train(String departureCity, String arrivalCity,
                     int startTime, int endTime, int duration) {
            if (departureCity == null || arrivalCity == null
                    || startTime < 0 || endTime < startTime
                    || duration < 0 || endTime - startTime != duration) {
                throw new IllegalArgumentException("Invalid train schedule");
            }
            this.departureCity = departureCity;
            this.arrivalCity = arrivalCity;
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = duration;
        }

        @Override
        public String toString() {
            return departureCity + " -> " + arrivalCity
                    + " (" + startTime + "-" + endTime + ")";
        }
    }

    private static class State {
        private final String city;
        private final int arrivalTime;

        private State(String city, int arrivalTime) {
            this.city = city;
            this.arrivalTime = arrivalTime;
        }
    }

    public static List<Train> findEarliestRoute(
            List<Train> trains, String source, String destination, int startTime) {
        if (trains == null || source == null || destination == null || startTime < 0) {
            throw new IllegalArgumentException("Invalid route request");
        }
        if (source.equals(destination)) {
            return new ArrayList<Train>();
        }

        // Group trains by departure city so each city can be explored quickly.
        Map<String, List<Train>> outgoing = new HashMap<String, List<Train>>();
        for (Train train : trains) {
            List<Train> routes = outgoing.get(train.departureCity);
            if (routes == null) {
                routes = new ArrayList<Train>();
                outgoing.put(train.departureCity, routes);
            }
            routes.add(train);
        }

        // Best known arrival time for every city.
        Map<String, Integer> earliestArrival = new HashMap<String, Integer>();
        // Train used to reach each city on its current best route.
        Map<String, Train> previousTrain = new HashMap<String, Train>();

        // Always process the city that can be reached earliest.
        PriorityQueue<State> queue = new PriorityQueue<State>(
                Comparator.comparingInt(state -> state.arrivalTime));

        earliestArrival.put(source, startTime);
        queue.offer(new State(source, startTime));

        while (!queue.isEmpty()) {
            State current = queue.poll();
            Integer knownArrival = earliestArrival.get(current.city);

            // A newer, faster route may already have replaced this queue entry.
            if (current.arrivalTime != knownArrival) {
                continue;
            }
            if (current.city.equals(destination)) {
                break;
            }

            List<Train> routes = outgoing.get(current.city);
            if (routes == null) {
                continue;
            }
            for (Train train : routes) {
                // The passenger must arrive before the train departs and may wait.
                if (train.startTime < current.arrivalTime) {
                    continue;
                }

                Integer nextArrival = earliestArrival.get(train.arrivalCity);

                // Taking this train gives a faster route to its arrival city.
                if (nextArrival == null || train.endTime < nextArrival) {
                    earliestArrival.put(train.arrivalCity, train.endTime);
                    previousTrain.put(train.arrivalCity, train);
                    queue.offer(new State(train.arrivalCity, train.endTime));
                }
            }
        }

        // No route can reach the destination.
        if (!earliestArrival.containsKey(destination)) {
            return new ArrayList<Train>();
        }

        // Follow the saved trains backward from destination to source.
        List<Train> route = new ArrayList<Train>();
        String city = destination;
        while (!city.equals(source)) {
            Train train = previousTrain.get(city);
            route.add(train);
            city = train.departureCity;
        }
        Collections.reverse(route);
        return route;
    }

    public static void main(String[] args) {
        List<Train> trains = Arrays.asList(
                new Train("A", "B", 1, 4, 3),
                new Train("A", "C", 2, 5, 3),
                new Train("B", "C", 5, 7, 2),
                new Train("B", "D", 6, 9, 3),
                new Train("C", "D", 6, 8, 2)
        );

        List<Train> route = findEarliestRoute(trains, "A", "D", 0);
        for (Train train : route) {
            System.out.println(train);
        }
    }
}
