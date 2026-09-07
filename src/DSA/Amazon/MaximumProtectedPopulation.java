package DSA.Amazon;

/*
 * Problem:
 * Given population[i] and a protection string, a protection at i can either
 * remain at i or move once to i - 1. A city can hold at most one protection.
 *
 * Greedy observation:
 * Consider one maximal consecutive run of protections [left, right].
 *
 * - Without moving, all cities in the run are protected.
 * - If the first protection moves to left - 1, every following protection can
 *   also move left, but the moves must form a prefix to avoid collisions.
 * - Any such prefix shift protects left - 1 and leaves exactly one city in
 *   the original run unprotected.
 *
 * Therefore, for a run preceded by a city left - 1:
 *   gain = population[left - 1] - minimum population in the run
 *
 * Take this gain only when it is positive. Runs starting at index 0 cannot
 * move left.
 *
 * Time Complexity: O(N)
 * Space Complexity: O(1)
 */
public class MaximumProtectedPopulation {

    public static long maximumProtectedPopulation(
            int[] populationOfCity, String protectionStatus) {
        if (populationOfCity == null || protectionStatus == null
                || populationOfCity.length != protectionStatus.length()) {
            throw new IllegalArgumentException("Population and status must have equal length");
        }

        long result = 0;
        int index = 0;

        while (index < populationOfCity.length) {
            if (protectionStatus.charAt(index) == '0') {
                index++;
                continue;
            }

            int left = index;
            long runSum = 0;
            int minimumInRun = Integer.MAX_VALUE;

            while (index < populationOfCity.length
                    && protectionStatus.charAt(index) == '1') {
                runSum += populationOfCity[index];
                minimumInRun = Math.min(minimumInRun, populationOfCity[index]);
                index++;
            }

            result += runSum;

            if (left > 0) {
                long gain = (long) populationOfCity[left - 1] - minimumInRun;
                result += Math.max(0L, gain);
            }
        }

        return result;
    }

    public static void main(String[] args) {
        int[] populationOfCity = {5, 6, 1, 4, 3};
        String protectionStatus = "01110";

        System.out.println(maximumProtectedPopulation(
                populationOfCity, protectionStatus)); // 15
    }
}
