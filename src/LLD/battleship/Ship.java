package LLD.battleship;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owned by an OceanBoard; no mutable ship reference escapes the game. */
final class Ship {
    private final ShipType type;
    private final Set<Coordinate> positions;
    private final Set<Coordinate> hits = new HashSet<>();

    Ship(ShipType type, List<Coordinate> positions) {
        this.type = type;
        this.positions = Set.copyOf(positions);
    }

    ShipType getType() {
        return type;
    }

    void hit(Coordinate coordinate) {
        if (!positions.contains(coordinate)) {
            throw new IllegalArgumentException("Shot is not on this ship");
        }
        hits.add(coordinate);
    }

    boolean isSunk() {
        return hits.size() == positions.size();
    }
}
