package DSA.Google;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Watchtower profit problem.
 * <p>
 * Q1: A town is building a watchtower. The watchtower is located at (0, 0).
 * Each unit height of the watchtower has a cost H.
 * There are N houses located at (x, y) coordinates.
 * Each house will pay cost C if it comes under the surveillance of the watchtower.
 * The horizontal distance covered by the watchtower is the same as it's height.
 * Find out the max profit you can make.
 * <p>
 * Inputs:
 * <p>
 * N number of houses
 * list of (x, y) coordinates
 * H cost to build unit height
 * C cost each house pays the watchtower
 * In the beginning the watchtower was located at the origin.
 * For the followup he said watchtower location will be provided. The (x, y) coordinates can be floats as well as H and C.
 *
 */
public class Worldtower {

    // HashMap to store house distances.
    // Time: O(h + d) where h is # of houses and d is the max distance between watchtower and the farthest house.
    // Space: O(u) where u is the # of unique distances among watchtower and all houses.
    private static int MaxProfit(int[] watchTower, int[][] houses, int h, int c) {
        Map<Integer, Integer> map = new HashMap<>();

        int maxHouseDistance = 0;
        for (int[] house : houses) {
            int houseDistance = distanceBetweenPoints(house, watchTower);
            maxHouseDistance = Math.max(maxHouseDistance, houseDistance);
            map.put(houseDistance, map.getOrDefault(houseDistance, 0) + 1);
        }

        int maxProfit = 0, revenue = 0;
        for (int d = 0; d <= maxHouseDistance; d++) {
            if (map.containsKey(d)) {
                revenue += map.get(d) * c;
                maxProfit = Math.max(maxProfit, revenue - d * h);
            }
        }

        return maxProfit;
    }

    private static int distanceBetweenPoints(int[] a, int[] b) {
        return (int) Math.ceil(
                Math.hypot(
                        Math.abs(a[0] - b[0]),
                        Math.abs(a[1] - b[1])));
    }

    /**
     * Follow-up: watchtower location is given and coordinates, H and C can all be floats.
     * <p>
     * Key insight: raising the height past a covered house without reaching a new house only
     * adds cost, so the optimal height is always exactly the (real) distance to some covered
     * house (or 0, i.e. build nothing). Sort houses by distance and, for every prefix of the
     * first k closest houses, the required height is the k-th distance, giving
     * profit = k * C - distance_k * H. Take the maximum, floored at 0.
     * <p>
     * Time: O(n log n) for the sort. Space: O(n) for the distances array.
     */
    private static double maxProfit(double[] watchTower, double[][] houses, double h, double c) {
        int n = houses.length;
        double[] distances = new double[n];
        for (int i = 0; i < n; i++) {
            distances[i] = Math.hypot(
                    houses[i][0] - watchTower[0],
                    houses[i][1] - watchTower[1]);
        }
        Arrays.sort(distances);

        double maxProfit = 0.0; // build nothing => 0 profit
        for (int k = 1; k <= n; k++) {
            // covering the k closest houses needs height = distances[k - 1]
            double profit = k * c - distances[k - 1] * h;
            maxProfit = Math.max(maxProfit, profit);
        }
        return maxProfit;
    }

    public static void main(String[] args) {
        int[] watchTower = {0, 0};
        int[][] houses = {
                {1, 0},   // distance 1
                {0, 1},   // distance 1
                {2, 2},   // distance ceil(2.83) = 3
                {3, 0},   // distance 3
        };
        int maintenancePerUnit = 1; // h
        int revenuePerHouse = 5;    // c

        int profit = MaxProfit(watchTower, houses, maintenancePerUnit, revenuePerHouse);
        System.out.println("Max profit: " + profit);
    }
}