package LLD.foodordering.exceptions;

/**
 * Thrown when an operation is attempted on an order that is not in a valid
 * state for it, e.g. trying to COMPLETE an order that isn't ACCEPTED, or
 * trying to CANCEL an order that has already been ACCEPTED by a restaurant.
 */
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
