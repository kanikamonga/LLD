package LLD.shoppingcart;

/**
 * Immutable product model.
 */
public final class Product {
    private final String id;
    private final String name;
    private final double price;

    public Product(String id, String name, double price) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id");
        if (name == null) throw new IllegalArgumentException("name");
        if (price < 0) throw new IllegalArgumentException("price must be >= 0");
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
}
