package LLD.CostExplorer;

import java.time.Month;
import java.util.LinkedHashMap;
import java.util.Map;

class Report {
	Map<Month, Double> monthlyCosts = new LinkedHashMap<>();
	double             yearlyEstimate;
	
	void printReport() {
		System.out.println("Monthly Costs:");
		monthlyCosts.forEach((month, cost) ->
				System.out.println(month + ": $" + cost));
		System.out.println("Yearly Estimate: $" + yearlyEstimate);
	}
}
