package LLD.LockerManagement;

public enum LockerSize {
    S, M, L, XL, XXL;

    public static LockerSize from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Locker size cannot be null");
        }

        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported locker size: " + value, exception);
        }
    }
}
