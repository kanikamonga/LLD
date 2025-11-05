package LLD.CostExplorer;

import java.util.ArrayList;
import java.util.List;

class Customer {
	private final String        customerId;
	private final List<Product> products = new ArrayList<>();
	
	public Customer(String id) {
		this.customerId = id;
	}
	
	public void addProduct(Product product) {
		products.add(product);
	}
	
	public List<Product> getProducts() {
		return products;
	}
	
	public String getCustomerId() {
		return customerId;
	}
}
