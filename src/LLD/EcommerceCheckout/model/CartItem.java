package LLD.EcommerceCheckout.model;

import java.math.BigDecimal;

/** Immutable product quantity and price snapshot in a cart. */
public final class CartItem {
    private final String productId;
    private final int quantity;
    private final BigDecimal unitPrice;

    public CartItem(String productId, int quantity, BigDecimal unitPrice) {
        if (productId == null || productId.trim().isEmpty()
                || quantity <= 0 || unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Invalid cart item");
        }
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }
    public String productId() { return productId; }
    public int quantity() { return quantity; }
    public BigDecimal unitPrice() { return unitPrice; }
    public BigDecimal total() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
