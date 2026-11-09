package LLD.battleship;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

/** Runs the actual console game loop through a complete deterministic match. */
public final class BattleshipDemo {
    static String setupCommands() {
        return "PLACE CARRIER A1 H\nPLACE BATTLESHIP B1 H\nPLACE CRUISER C1 H\n"
                + "PLACE SUBMARINE D1 H\nPLACE DESTROYER E1 H\nREADY\n";
    }

    static String winningMatchCommands() {
        StringBuilder commands = new StringBuilder(setupCommands()).append(setupCommands());
        int opponentShots = 0;
        int shipRow = 0;
        for (ShipType type : ShipType.values()) {
            for (int column = 0; column < type.getLength(); column++) {
                commands.append("FIRE ").append(new Coordinate(shipRow, column)).append('\n');
                if (shipRow != 4 || column != 1) {
                    commands.append("FIRE ")
                            .append(new Coordinate(8 + opponentShots / 10, opponentShots % 10)).append('\n');
                    opponentShots++;
                }
            }
            shipRow++;
        }
        return commands.toString();
    }

    public static void main(String[] args) throws IOException {
        new BattleshipConsole(new BattleshipGame("Player1", "Player2"),
                new BufferedReader(new StringReader(winningMatchCommands())),
                new PrintWriter(System.out, true)).run();
    }
}
