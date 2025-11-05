package LLD.CostExplorer;

import LLD.CostExplorer.PlanType;

import java.time.LocalDate;

class Subscription {
	private final PlanType  plan;
	private final LocalDate startDate;
	private final LocalDate endDate; // null if ongoing
	
	public Subscription(PlanType plan, LocalDate startDate, LocalDate endDate) {
		this.plan = plan;
		this.startDate = startDate;
		this.endDate = endDate;
	}
	
	public PlanType getPlan() {
		return plan;
	}
	
	public LocalDate getStartDate() {
		return startDate;
	}
	
	public LocalDate getEndDate() {
		return endDate;
	}
	
	public boolean isActiveOn(LocalDate date) {
		return (date.isEqual(startDate) || date.isAfter(startDate)) &&
				(endDate == null || date.isBefore(endDate) || date.isEqual(endDate));
	}
}
