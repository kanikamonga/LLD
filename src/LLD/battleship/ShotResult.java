package LLD.battleship;

import java.util.Objects;
import java.util.Optional;

/** Outcome reveals ship identity only when it is sunk, never its other positions. */
public final class ShotResult {
    public enum Outcome { MISS, HIT, SUNK }

    private final Coordinate coordinate;
    private final Outcome outcome;
    private final ShipType sunkShip;

    ShotResult(Coordinate coordinate, Outcome outcome, ShipType sunkShip) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        if ((outcome == Outcome.SUNK) != (sunkShip != null)) {
            throw new IllegalArgumentException("Only a sunk result must identify a ship");
        }
        this.sunkShip = sunkShip;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Optional<ShipType> getSunkShip() {
        return Optional.ofNullable(sunkShip);
    }

    @Override
    public String toString() {
        return coordinate + ": " + outcome + (sunkShip == null ? "" : " " + sunkShip);
    }
}
