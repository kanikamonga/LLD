package LLD.CostExplorer;

import java.util.ArrayList;
import java.util.List;

class Product {
	private final String             name;
	private final List<Subscription> subscriptions = new ArrayList<>();
	
	public Product(String name) {
		this.name = name;
	}
	
	public void addSubscription(Subscription subscription) {
		subscriptions.add(subscription);
	}
	
	public List<Subscription> getSubscriptions() {
		return subscriptions;
	}
	
	public String getName() {
		return name;
	}
}
