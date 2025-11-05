package LLD.CustomerSupportSystem;

import LLD.CustomerSupportSystem.service.RatingService;
import LLD.CustomerSupportSystem.strategy.CountDescTieBreaker;
import LLD.CustomerSupportSystem.strategy.NameAscTieBreaker;
import LLD.CustomerSupportSystem.strategy.SumDescTieBreaker;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public class Demo {
    public static void main(String[] args) {
        RatingService svc = new RatingService();
        YearMonth aug = YearMonth.of(2025, 8);

        // Create a 4.0 vs 4.0 tie, broken by count or sum or name based on chain
        svc.addRating("Alice", 5, aug); // avg=4.0 (5,3) count=2 sum=8
        svc.addRating("Alice", 3, aug);
        svc.addRating("Bob", 4, aug);   // avg=4.0 count=1 sum=4
        svc.addRating("Charlie", 5, aug);
        svc.addRating("Charlie", 4, aug); // avg=4.5

        // (1) Break ties by COUNT, then NAME
        var overall1 = svc.getOverallAverageRatings(new CountDescTieBreaker(), new NameAscTieBreaker());
        print("Overall (COUNT -> NAME)", overall1);

        // (2) Break ties by NAME only (Alice wins over Bob on name)
        var overall2 = svc.getOverallAverageRatings(new NameAscTieBreaker());
        print("Overall (NAME only)", overall2);

        // (3) Break ties by SUM, then NAME
        var monthlyAug = svc.getMonthlyAverageRatings(aug, new SumDescTieBreaker(), new NameAscTieBreaker());
        print("Aug (SUM -> NAME)", monthlyAug);
    }

    private static void print(String title, List<Map.Entry<String, Double>> rows) {
        System.out.println("\n" + title);
        rows.forEach(e -> System.out.printf("%s -> %.2f%n", e.getKey(), e.getValue()));
    }
}
