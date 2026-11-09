package LLD.battleship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stores only outcomes of fired missiles, never the opponent's hidden fleet. */
final class TrackingBoard {
    private final Map<Coordinate, ShotResult> shots = new HashMap<>();

    boolean hasShot(Coordinate coordinate) {
        return shots.containsKey(coordinate);
    }

    void record(ShotResult result) {
        shots.put(result.getCoordinate(), result);
    }

    List<String> render() {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < Coordinate.BOARD_SIZE; row++) {
            StringBuilder cells = new StringBuilder();
            for (int column = 0; column < Coordinate.BOARD_SIZE; column++) {
                ShotResult result = shots.get(new Coordinate(row, column));
                cells.append(result == null ? '.'
                        : result.getOutcome() == ShotResult.Outcome.MISS ? 'o' : 'X');
            }
            rows.add(cells.toString());
        }
        return List.copyOf(rows);
    }
}
