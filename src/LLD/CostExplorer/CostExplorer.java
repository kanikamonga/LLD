package LLD.CostExplorer;

import LLD.CostExplorer.Customer;
import LLD.CostExplorer.Product;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class CostExplorer {
	
	// Generate monthly bill for the unit year (Jan to Dec of given year)
	public Map<YearMonth, Double> generateMonthlyReport(Customer customer, int year) {
		Map<YearMonth, Double> monthlyCosts = new LinkedHashMap<>();
		
		for (YearMonth ym : getAllMonths(year)) {
			double monthlyTotal = 0.0;
			for (Product product : customer.getProducts()) {
				for (Subscription sub : product.getSubscriptions()) {
					if (isSubscriptionActiveInMonth(sub, ym)) {
						monthlyTotal += sub.getPlan().getMonthlyRate();
					}
				}
			}
			monthlyCosts.put(ym, monthlyTotal);
		}
		
		return monthlyCosts;
	}
	
	// Yearly aggregation
	public double generateYearlyEstimate(Customer customer, int year) {
		Map<YearMonth, Double> monthly = generateMonthlyReport(customer, year);
		return monthly.values().stream().mapToDouble(Double::doubleValue).sum();
	}
	
	// Helper methods
	private List<YearMonth> getAllMonths(int year) {
		List<YearMonth> months = new ArrayList<>();
		for (int m = 1; m <= 12; m++) {
			months.add(YearMonth.of(year, m));
		}
		return months;
	}
	
	private boolean isSubscriptionActiveInMonth(Subscription sub, YearMonth ym) {
		LocalDate firstDay = ym.atDay(1);
		LocalDate lastDay  = ym.atEndOfMonth();
		return (sub.getStartDate().isBefore(lastDay) || sub.getStartDate().isEqual(lastDay)) &&
				(sub.getEndDate() == null || sub.getEndDate().isAfter(firstDay) || sub.getEndDate().isEqual(firstDay));
	}
}
