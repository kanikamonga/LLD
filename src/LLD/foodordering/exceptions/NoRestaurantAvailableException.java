package LLD.foodordering.exceptions;

/**
 * Thrown when an order cannot be assigned to ANY restaurant because either
 * no single restaurant's menu covers all requested items, or every eligible
 * restaurant is already at full processing capacity.
 */
public class NoRestaurantAvailableException extends RuntimeException {
    public NoRestaurantAvailableException(String message) {
        super(message);
    }
}
