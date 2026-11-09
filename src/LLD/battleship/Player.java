package LLD.battleship;

/** Package-private aggregate owned by the game. */
final class Player {
    private final String name;
    private final OceanBoard ocean = new OceanBoard();
    private final TrackingBoard tracking = new TrackingBoard();

    Player(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        this.name = name;
    }

    String getName() {
        return name;
    }

    OceanBoard getOcean() {
        return ocean;
    }

    TrackingBoard getTracking() {
        return tracking;
    }
}
