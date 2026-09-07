package LLD.shoppingcart;

/**
 * Minimal product catalog interface.
 */
public interface ProductCatalog {
    /**
     * Return product by id, or null if not found
     */
    Product getProduct(String productId);

    /**
     * Check available stock for a product
     */
    int availableQuantity(String productId);

    /**
     * Reserve quantity (used at checkout). Returns true if reserved, false otherwise.
     */
    boolean reserve(String productId, int quantity);

    /**
     * Release reserved quantity (e.g., on checkout failure)
     */
    void release(String productId, int quantity);
}
