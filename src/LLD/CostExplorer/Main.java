package LLD.CostExplorer;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Customer c1 = new Customer("C1");

        Product jira = new Product("Jira");
        jira.addSubscription(new Subscription(PlanType.BASIC,
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2022, 3, 31)));

        jira.addSubscription(new Subscription(PlanType.PREMIUM,
                LocalDate.of(2022, 4, 1),
                null)); // ongoing

        c1.addProduct(jira);

        CostExplorer explorer = new CostExplorer();

        Map<YearMonth, Double> monthlyReport = explorer.generateMonthlyReport(c1, 2022);
        System.out.println("Monthly Report:");
        monthlyReport.forEach((month, cost) ->
                System.out.println(month + " -> $" + cost));

        double yearlyEstimate = explorer.generateYearlyEstimate(c1, 2022);
        System.out.println("\nYearly Estimate for 2022: $" + yearlyEstimate);
    }
}
