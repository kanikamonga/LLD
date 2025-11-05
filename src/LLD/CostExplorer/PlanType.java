package LLD.CostExplorer;

import java.time.LocalDate;

enum PlanType {
    BASIC(9.99),
    STANDARD(49.99),
    PREMIUM(249.99);

    private final double monthlyRate;

    PlanType(double rate) {
        this.monthlyRate = rate;
    }

    public double getMonthlyRate() {
        return monthlyRate;
    }
}


