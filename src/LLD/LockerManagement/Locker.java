package LLD.LockerManagement;

public final class Locker {
    private final String id;
    private final LockerSize size;
    private boolean occupied;

    Locker(String id, LockerSize size) {
        this.id = id;
        this.size = size;
    }

    public String getId() {
        return id;
    }

    public LockerSize getSize() {
        return size;
    }

    public boolean isOccupied() {
        return occupied;
    }

    boolean occupy() {
        if (occupied) {
            return false;
        }
        occupied = true;
        return true;
    }

    boolean release() {
        if (!occupied) {
            return false;
        }
        occupied = false;
        return true;
    }
}
