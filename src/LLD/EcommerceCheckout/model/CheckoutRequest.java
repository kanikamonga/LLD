package LLD.EcommerceCheckout.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable command submitted to the checkout service. */
public final class CheckoutRequest {
    private final String userId;
    private final String idempotencyKey;
    private final List<CartItem> items;
    private final PaymentDetails paymentDetails;

    public CheckoutRequest(String userId, String idempotencyKey,
                           List<CartItem> items, PaymentDetails paymentDetails) {
        this.userId = required(userId, "user id");
        this.idempotencyKey = required(idempotencyKey, "idempotency key");
        if (items == null || items.isEmpty() || paymentDetails == null) {
            throw new IllegalArgumentException("Items and payment details are required");
        }
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.paymentDetails = paymentDetails;
    }
    public String userId() { return userId; }
    public String idempotencyKey() { return idempotencyKey; }
    public List<CartItem> items() { return items; }
    public PaymentDetails paymentDetails() { return paymentDetails; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
