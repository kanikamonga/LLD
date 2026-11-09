package LLD.battleship;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

/** Dependency-free domain and end-to-end console tests. */
public final class BattleshipTest {
    public static void main(String[] args) throws Exception {
        coordinatesAndPlacement();
        turnsAndTracking();
        fullMatch(false);
        fullMatch(true);
        consoleLoop();
        System.out.println("All Battleship tests passed");
    }

    private static void coordinatesAndPlacement() {
        equal(new Coordinate(9, 9), Coordinate.parse("j10"));
        equal(new Coordinate(9, 9).hashCode(), Coordinate.parse("J10").hashCode());
        expect(IllegalArgumentException.class, () -> Coordinate.parse("A0"));
        expect(IllegalArgumentException.class, () -> Coordinate.parse("K1"));
        expect(IllegalArgumentException.class, () -> Coordinate.parse("A11"));
        expect(IllegalArgumentException.class, () -> new BattleshipGame("A", "A"));
        BattleshipGame game = new BattleshipGame("A", "B");
        expect(IllegalStateException.class, game::start);
        expect(IllegalStateException.class, () -> game.fire("A", Coordinate.parse("A1")));
        expect(IllegalArgumentException.class, () -> game.placeShip("A", ShipType.CARRIER,
                Coordinate.parse("A7"), Orientation.HORIZONTAL));
        equal("..........", game.getOwnBoard("A").get(0));
        game.placeShip("A", ShipType.CARRIER, Coordinate.parse("A1"), Orientation.HORIZONTAL);
        expect(IllegalStateException.class, () -> game.placeShip("A", ShipType.CARRIER,
                Coordinate.parse("B1"), Orientation.HORIZONTAL));
        expect(IllegalArgumentException.class, () -> game.placeShip("A", ShipType.DESTROYER,
                Coordinate.parse("A5"), Orientation.VERTICAL));
        equal("..........", game.getOwnBoard("A").get(1));
        game.placeShip("A", ShipType.DESTROYER, Coordinate.parse("I10"), Orientation.VERTICAL);
        equal('S', game.getOwnBoard("A").get(9).charAt(9));
        expect(IllegalArgumentException.class, () -> game.getOwnBoard("unknown"));
    }

    private static BattleshipGame readyGame() {
        BattleshipGame game = new BattleshipGame("A", "B");
        for (String player : game.getPlayerNames()) {
            int row = 0;
            for (ShipType type : ShipType.values()) {
                game.placeShip(player, type, new Coordinate(row++, 0), Orientation.HORIZONTAL);
            }
        }
        game.start();
        return game;
    }

    private static void turnsAndTracking() {
        BattleshipGame game = readyGame();
        equal("A", game.getCurrentPlayer());
        expect(IllegalStateException.class, () -> game.fire("B", Coordinate.parse("J10")));
        expect(IllegalStateException.class, () -> game.placeShip("A", ShipType.CARRIER,
                Coordinate.parse("F1"), Orientation.HORIZONTAL));
        expect(IllegalStateException.class, game::start);
        equal(List.of("..........", "..........", "..........", "..........", "..........",
                "..........", "..........", "..........", "..........", ".........."),
                game.getTrackingBoard("A"));
        List<String> oldView = game.getTrackingBoard("A");
        equal(ShotResult.Outcome.HIT, game.fire("A", Coordinate.parse("A1")).getOutcome());
        equal('X', game.getTrackingBoard("A").get(0).charAt(0));
        equal('X', game.getOwnBoard("B").get(0).charAt(0));
        equal("..........", oldView.get(0));
        expect(UnsupportedOperationException.class, () -> oldView.clear());
        ShotResult miss = game.fire("B", Coordinate.parse("J10"));
        equal(ShotResult.Outcome.MISS, miss.getOutcome());
        equal(true, miss.getSunkShip().isEmpty());
        equal('o', game.getOwnBoard("A").get(9).charAt(9));
        expect(IllegalArgumentException.class, () -> game.fire("A", Coordinate.parse("A1")));
        equal("A", game.getCurrentPlayer()); // Invalid shots do not consume the turn.
        game.fire("A", Coordinate.parse("J10"));
        expect(IllegalArgumentException.class, () -> game.fire("B", Coordinate.parse("J10")));
        equal("B", game.getCurrentPlayer());
    }

    private static void fullMatch(boolean secondPlayerWins) {
        BattleshipGame game = readyGame();
        String attacker = secondPlayerWins ? "B" : "A";
        String defender = secondPlayerWins ? "A" : "B";
        int misses = 0;
        int row = 0;
        int sunk = 0;
        for (ShipType type : ShipType.values()) {
            for (int column = 0; column < type.getLength(); column++) {
                if (secondPlayerWins) {
                    game.fire(defender, new Coordinate(8 + misses / 10, misses % 10));
                    misses++;
                }
                ShotResult result = game.fire(attacker, new Coordinate(row, column));
                if (column == type.getLength() - 1) {
                    equal(ShotResult.Outcome.SUNK, result.getOutcome());
                    equal(type, result.getSunkShip().orElseThrow());
                    sunk++;
                } else {
                    equal(ShotResult.Outcome.HIT, result.getOutcome());
                    equal(true, result.getSunkShip().isEmpty());
                }
                if (!secondPlayerWins && game.getPhase() == BattleshipGame.Phase.IN_PROGRESS) {
                    game.fire(defender, new Coordinate(8 + misses / 10, misses % 10));
                    misses++;
                }
            }
            row++;
        }
        equal(5, sunk);
        equal(BattleshipGame.Phase.FINISHED, game.getPhase());
        equal(attacker, game.getWinner().orElseThrow());
        expect(IllegalStateException.class, () -> game.fire(defender, Coordinate.parse("F1")));
    }

    private static void consoleLoop() throws Exception {
        StringWriter text = new StringWriter();
        String script = "READY\nPLACE DESTROYER A10 H\nDRAW BOARD\n"
                + BattleshipDemo.winningMatchCommands().replaceFirst(
                        "FIRE A1", "DRAW BOARD\nFIRE K1\nFIRE A1");
        BattleshipGame game = new BattleshipGame("Player1", "Player2");
        new BattleshipConsole(game, new BufferedReader(new StringReader(script)),
                new PrintWriter(text, true)).run();
        equal(BattleshipGame.Phase.FINISHED, game.getPhase());
        if (!text.toString().contains("Winner: Player1")
                || !text.toString().contains("Error:")
                || !text.toString().contains("FIRED MISSILES")) {
            throw new AssertionError("Console must handle errors, drawing and victory");
        }
        StringWriter eof = new StringWriter();
        new BattleshipConsole(new BattleshipGame("A", "B"),
                new BufferedReader(new StringReader("")), new PrintWriter(eof, true)).run();
        if (!eof.toString().contains("Input ended")) {
            throw new AssertionError("EOF must stop the loop");
        }
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", received " + actual);
        }
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException e) {
            if (!type.isInstance(e)) throw e;
            return;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
