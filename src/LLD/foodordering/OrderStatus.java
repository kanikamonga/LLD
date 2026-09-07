package LLD.foodordering;

public enum OrderStatus {
    /** Order created but not yet assigned to a restaurant. Transient internal state. */
    PENDING,
    /** Assigned to a restaurant which is now preparing it; counts against that restaurant's capacity. */
    ACCEPTED,
    /** Restaurant finished preparing the order; capacity has been released. Terminal state. */
    COMPLETED,
    /** Could not be assigned to any restaurant (no single restaurant covers all items, or all at capacity). Terminal state. */
    REJECTED
}
