package LLD.underground;

/** Station identity is its exact name; names are not trimmed or case-normalized. */
public final class Station {
    private final String name;

    public Station(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Station name must not be blank");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Station)) return false;
        Station station = (Station) other;
        return name.equals(station.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Station[name=" + name + "]";
    }
}
