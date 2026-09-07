package LLD.PaymentGateway.model;

/** Merchant/client onboarded to use the payment gateway. */
public final class Client {
    private final String id;
    private final String name;

    public Client(String id, String name) {
        this.id = required(id, "client id");
        this.name = required(name, "client name");
    }

    public String id() { return id; }
    public String name() { return name; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
