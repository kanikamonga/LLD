package LLD.foodordering;

/**
 * A single priced item on a restaurant's menu.
 * Name is immutable (identity of the item); price can be updated later
 * (menu update use-case), but items can never be removed per requirements.
 */
public final class MenuItem {
    private final String name;
    private volatile double price;

    public MenuItem(String name, double price) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Menu item name must not be blank");
        }
        if (price < 0) {
            throw new IllegalArgumentException("Price must be >= 0");
        }
        this.name = name;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    void updatePrice(double newPrice) {
        if (newPrice < 0) {
            throw new IllegalArgumentException("Price must be >= 0");
        }
        this.price = newPrice;
    }
}
