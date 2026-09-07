package DSA.Google;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Problem:
 * There are n islands. Each island has a value and some pairs of islands are
 * directly connected. Select three distinct islands A, B, and C such that
 * A-B and B-C are direct connections. Maximize value[A] + value[B] + value[C].
 *
 * This treats "connected group of 3" as a chain A-B-C. A and C do not need to
 * be directly connected to each other.
 *
 * Approach:
 * For every island B, only its two highest-valued distinct neighbors can
 * produce the best chain through B. There is no need to run BFS: every valid
 * group has one middle island, and checking every possible middle island
 * covers every group.
 *
 * Time Complexity: O(n + e), where e is the number of connections.
 * Space Complexity: O(n + e).
 */
public class MaximumValueThreeIslandChain {

    public static class Group {
        public final int first;
        public final int middle;
        public final int third;
        public final long sum;

        private Group(int first, int middle, int third, long sum) {
            this.first = first;
            this.middle = middle;
            this.third = third;
            this.sum = sum;
        }

        @Override
        public String toString() {
            return "Island group: [" + first + ", " + middle + ", " + third
                    + "], maximum sum: " + sum;
        }
    }

    public static Group findMaximumGroup(long[] values, int[][] connections) {
        if (values == null || connections == null) {
            throw new IllegalArgumentException("Values and connections are required");
        }

        List<Set<Integer>> neighbors = new ArrayList<Set<Integer>>(values.length);
        for (int i = 0; i < values.length; i++) {
            neighbors.add(new HashSet<Integer>());
        }

        // Connections are undirected: an edge u-v can be traversed both ways.
        for (int[] connection : connections) {
            if (connection == null || connection.length != 2
                    || !isValidIsland(connection[0], values.length)
                    || !isValidIsland(connection[1], values.length)
                    || connection[0] == connection[1]) {
                throw new IllegalArgumentException("Invalid island connection");
            }
            neighbors.get(connection[0]).add(connection[1]);
            neighbors.get(connection[1]).add(connection[0]);
        }

        Group best = null;

        for (int middle = 0; middle < values.length; middle++) {
            int bestNeighbor = -1;
            int secondBestNeighbor = -1;

            // Keep only the two largest neighbor values for this middle island.
            for (int neighbor : neighbors.get(middle)) {
                if (bestNeighbor == -1
                        || values[neighbor] > values[bestNeighbor]) {
                    secondBestNeighbor = bestNeighbor;
                    bestNeighbor = neighbor;
                } else if (secondBestNeighbor == -1
                        || values[neighbor] > values[secondBestNeighbor]) {
                    secondBestNeighbor = neighbor;
                }
            }

            if (secondBestNeighbor == -1) {
                continue;
            }

            long sum = values[bestNeighbor] + values[middle]
                    + values[secondBestNeighbor];
            if (best == null || sum > best.sum) {
                best = new Group(bestNeighbor, middle, secondBestNeighbor, sum);
            }
        }

        return best;
    }

    private static boolean isValidIsland(int island, int islandCount) {
        return island >= 0 && island < islandCount;
    }

    public static void main(String[] args) {
        long[] values = {8, 3, 10, 6, 7};
        int[][] connections = {
                {0, 1}, {0, 2}, {0, 3},
                {2, 3}, {2, 4}
        };

        System.out.println(findMaximumGroup(values, connections));
    }
}
