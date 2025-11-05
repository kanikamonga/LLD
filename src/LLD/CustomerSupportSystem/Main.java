package LLD.CustomerSupportSystem;

import LLD.CustomerSupportSystem.service.RatingService;

import java.time.YearMonth;

public class Main {
    public static void main(String[] args) {
        RatingService service = new RatingService();

        YearMonth aug = YearMonth.of(2025, 8);
        YearMonth sep = YearMonth.of(2025, 9);

        service.addRating("Alice", 5, aug);
        service.addRating("Bob", 4, aug);
        service.addRating("Alice", 3, aug);
        service.addRating("Charlie", 5, sep);
        service.addRating("Charlie", 4, sep);

        System.out.println("Overall Ratings:");
        service.getOverallAverageRatings().forEach(e ->
                System.out.println(e.getKey() + " -> " + e.getValue()));

        System.out.println("\nAugust Ratings:");
        service.getMonthlyAverageRatings(aug).forEach(e ->
                System.out.println(e.getKey() + " -> " + e.getValue()));

        System.out.println("\nExport Monthly:");
        System.out.println(service.exportMonthlyAverages());
    }
}
