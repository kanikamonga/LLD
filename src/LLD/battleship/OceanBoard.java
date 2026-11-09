package LLD.battleship;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Placement and incoming-shot rules. All access is coordinated by BattleshipGame. */
final class OceanBoard {
    private final Map<ShipType, Ship> fleet = new EnumMap<>(ShipType.class);
    private final Map<Coordinate, Ship> occupied = new HashMap<>();
    private final Map<Coordinate, ShotResult> receivedShots = new HashMap<>();

    void place(ShipType type, Coordinate start, Orientation orientation) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(orientation, "orientation");
        if (fleet.containsKey(type)) {
            throw new IllegalStateException("Ship already placed: " + type);
        }
        List<Coordinate> positions = new ArrayList<>();
        for (int offset = 0; offset < type.getLength(); offset++) {
            Coordinate position = new Coordinate(
                    start.getRow() + (orientation == Orientation.VERTICAL ? offset : 0),
                    start.getColumn() + (orientation == Orientation.HORIZONTAL ? offset : 0));
            if (occupied.containsKey(position)) {
                throw new IllegalArgumentException("Ships overlap at " + position);
            }
            positions.add(position);
        }
        // Validate the entire placement before publishing any part of the ship.
        Ship ship = new Ship(type, positions);
        fleet.put(type, ship);
        positions.forEach(position -> occupied.put(position, ship));
    }

    boolean isReady() {
        return fleet.size() == ShipType.values().length;
    }

    ShotResult receiveShot(Coordinate coordinate) {
        if (receivedShots.containsKey(coordinate)) {
            throw new IllegalArgumentException("Coordinate already targeted: " + coordinate);
        }
        Ship ship = occupied.get(coordinate);
        ShotResult result;
        if (ship == null) {
            result = new ShotResult(coordinate, ShotResult.Outcome.MISS, null);
        } else {
            ship.hit(coordinate);
            result = ship.isSunk()
                    ? new ShotResult(coordinate, ShotResult.Outcome.SUNK, ship.getType())
                    : new ShotResult(coordinate, ShotResult.Outcome.HIT, null);
        }
        receivedShots.put(coordinate, result);
        return result;
    }

    boolean allSunk() {
        return isReady() && fleet.values().stream().allMatch(Ship::isSunk);
    }

    List<String> renderOwnBoard() {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < Coordinate.BOARD_SIZE; row++) {
            StringBuilder cells = new StringBuilder();
            for (int column = 0; column < Coordinate.BOARD_SIZE; column++) {
                Coordinate coordinate = new Coordinate(row, column);
                ShotResult result = receivedShots.get(coordinate);
                cells.append(result == null ? (occupied.containsKey(coordinate) ? 'S' : '.')
                        : result.getOutcome() == ShotResult.Outcome.MISS ? 'o' : 'X');
            }
            rows.add(cells.toString());
        }
        return List.copyOf(rows);
    }
}
