import java.util.*;

/**
 * RandomCountryPicker - Weighted Random Selection by Population
 * 
 * Problem: Given a list of countries with populations, return a random country
 * where the probability of selection is proportional to its population.
 * 
 * Time Complexity: O(n) for setup, O(log n) for each pick() using binary search
 * Space Complexity: O(n)
 * 
 * Approach: Use prefix sum array with binary search
 * - Calculate cumulative populations
 * - Generate random number and use binary search to find the country
 */
public class RandomCountryPicker {
    private String[] countries;
    private long[] prefixSums;
    private long totalPopulation;
    private Random random;

    /**
     * Constructor to initialize the country picker with population data
     * @param populationPerCountry Map of country name to population
     */
    public RandomCountryPicker(Map<String, Long> populationPerCountry) {
        if (populationPerCountry == null || populationPerCountry.isEmpty()) {
            throw new IllegalArgumentException("Population map cannot be null or empty");
        }

        int n = populationPerCountry.size();
        countries = new String[n];
        prefixSums = new long[n];
        random = new Random();
        totalPopulation = 0;

        int index = 0;
        for (Map.Entry<String, Long> entry : populationPerCountry.entrySet()) {
            countries[index] = entry.getKey();
            long population = entry.getValue();
            
            if (population <= 0) {
                throw new IllegalArgumentException("Population must be positive for country: " + entry.getKey());
            }
            
            totalPopulation += population;
            prefixSums[index] = totalPopulation;
            index++;
        }
    }

    /**
     * Pick a random country proportional to its population
     * @return A random country based on population weights
     */
    public String pick() {
        // Generate random number between 0 and totalPopulation (exclusive)
        long randomValue = (long) (random.nextDouble() * totalPopulation);
        
        // Binary search to find the country
        int index = binarySearch(randomValue);
        return countries[index];
    }

    /**
     * Binary search to find the country index for given random value
     * @param randomValue Random value between 0 and totalPopulation
     * @return Index of the country
     */
    private int binarySearch(long randomValue) {
        int left = 0, right = prefixSums.length - 1;
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (randomValue < prefixSums[mid]) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        
        return left;
    }

    /**
     * Alternative simpler approach without binary search
     * Time: O(n) per pick, but simpler to understand
     */
    public String pickSimple() {
        long randomValue = (long) (random.nextDouble() * totalPopulation);
        long sum = 0;
        
        for (int i = 0; i < countries.length; i++) {
            if (randomValue < prefixSums[i]) {
                return countries[i];
            }
        }
        
        return countries[countries.length - 1];
    }

    // Test the implementation
    public static void main(String[] args) {
        // Example 1
        Map<String, Long> populationPerCountry1 = new LinkedHashMap<>();
        populationPerCountry1.put("USA", 300000000L);
        populationPerCountry1.put("Russia", 150000000L);
        populationPerCountry1.put("India", 2000000000L);
        populationPerCountry1.put("Spain", 300000000L);

        RandomCountryPicker picker1 = new RandomCountryPicker(populationPerCountry1);
        
        System.out.println("Example 1: Random picks from 4 countries");
        System.out.println("Population distribution:");
        populationPerCountry1.forEach((country, population) -> 
            System.out.println("  " + country + ": " + population)
        );
        System.out.println("\n10 random picks:");
        for (int i = 0; i < 10; i++) {
            System.out.println("  " + picker1.pick());
        }

        // Example 2
        System.out.println("\n" + "=".repeat(50));
        Map<String, Long> populationPerCountry2 = new LinkedHashMap<>();
        populationPerCountry2.put("China", 1400000000L);
        populationPerCountry2.put("Nigeria", 200000000L);
        populationPerCountry2.put("Brazil", 210000000L);
        populationPerCountry2.put("Germany", 83000000L);

        RandomCountryPicker picker2 = new RandomCountryPicker(populationPerCountry2);
        
        System.out.println("\nExample 2: Random picks from 4 countries");
        System.out.println("Population distribution:");
        populationPerCountry2.forEach((country, population) -> 
            System.out.println("  " + country + ": " + population)
        );
        System.out.println("\n10 random picks:");
        for (int i = 0; i < 10; i++) {
            System.out.println("  " + picker2.pick());
        }

        // Statistical verification - show probability distribution
        System.out.println("\n" + "=".repeat(50));
        System.out.println("\nStatistical Verification (1000 picks):");
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String country = picker2.pick();
            counts.put(country, counts.getOrDefault(country, 0) + 1);
        }
        
        System.out.println("Country\t\tCount\t\tExpected Probability\tActual Probability");
        long totalPop = populationPerCountry2.values().stream().mapToLong(Long::longValue).sum();
        populationPerCountry2.forEach((country, population) -> {
            double expectedProb = (double) population / totalPop * 100;
            double actualProb = (double) counts.getOrDefault(country, 0) / 1000 * 100;
            System.out.printf("%s\t\t%d\t\t%.2f%%\t\t\t%.2f%%\n", 
                country, 
                counts.getOrDefault(country, 0), 
                expectedProb, 
                actualProb);
        });
    }
}
