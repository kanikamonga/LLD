package LLD.battleship;

import java.util.Locale;
import java.util.Objects;

/** Immutable board position: A1..J10. */
public final class Coordinate {
    public static final int BOARD_SIZE = 10;
    private final int row;
    private final int column;

    public Coordinate(int row, int column) {
        if (row < 0 || row >= BOARD_SIZE || column < 0 || column >= BOARD_SIZE) {
            throw new IllegalArgumentException("Coordinate must be within A1..J10");
        }
        this.row = row;
        this.column = column;
    }

    public static Coordinate parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Missing coordinate");
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-J](?:[1-9]|10)")) {
            throw new IllegalArgumentException("Coordinate must be A1..J10");
        }
        return new Coordinate(normalized.charAt(0) - 'A', Integer.parseInt(normalized.substring(1)) - 1);
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Coordinate)) return false;
        Coordinate coordinate = (Coordinate) other;
        return row == coordinate.row && column == coordinate.column;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, column);
    }

    @Override
    public String toString() {
        return String.valueOf((char) ('A' + row)) + (column + 1);
    }
}
