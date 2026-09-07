package LLD.EcommerceCheckout.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Aggregate root for checkout state and order lifecycle transitions. */
public final class Order {
    public enum Status { PENDING_PAYMENT, CONFIRMED, CANCELLED }

    private final String id = UUID.randomUUID().toString();
    private final String userId;
    private final List<CartItem> items;
    private final BigDecimal total;
    private Status status = Status.PENDING_PAYMENT;

    public Order(String userId, List<CartItem> items) {
        this.userId = userId;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.total = items.stream().map(CartItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public String id() { return id; }
    public String userId() { return userId; }
    public List<CartItem> items() { return items; }
    public BigDecimal total() { return total; }
    public Status status() { return status; }
    public void confirm() {
        if (status != Status.PENDING_PAYMENT) throw new IllegalStateException("Invalid order state");
        status = Status.CONFIRMED;
    }
    public void cancel() {
        if (status == Status.CONFIRMED) throw new IllegalStateException("Confirmed order cannot cancel here");
        status = Status.CANCELLED;
    }
}
