package LLD.shoppingcart;

public final class CartItem {
    private final Product product;
    private int quantity;

    public CartItem(Product product, int quantity) {
        if (product == null) throw new IllegalArgumentException("product");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int q) {
        if (q <= 0) throw new IllegalArgumentException("quantity must be > 0");
        this.quantity = q;
    }

    public double totalPrice() { return product.getPrice() * quantity; }
}
