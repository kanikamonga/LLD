package LLD.foodordering.exceptions;

public class DuplicateRestaurantException extends RuntimeException {
    public DuplicateRestaurantException(String message) {
        super(message);
    }
}
