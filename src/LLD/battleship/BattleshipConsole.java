package LLD.battleship;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

/** Console adapter and game loop; input/output are injectable for scripted play. */
public final class BattleshipConsole {
    private final BattleshipGame game;
    private final BufferedReader input;
    private final PrintWriter output;

    public BattleshipConsole(BattleshipGame game, BufferedReader input, PrintWriter output) {
        this.game = java.util.Objects.requireNonNull(game, "game");
        this.input = java.util.Objects.requireNonNull(input, "input");
        this.output = java.util.Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Coordinates A1..J10; rows A..J, columns 1..10. QUIT exits.");
        for (String player : game.getPlayerNames()) {
            output.println(player + " setup: PLACE <ship> <coordinate> H|V; DRAW BOARD; READY");
            while (true) {
                String command = readCommand(player);
                if (command == null || command.equals("QUIT")) return;
                try {
                    if (command.equals("READY")) {
                        if (!game.isReady(player)) {
                            throw new IllegalStateException("Place all five ships before READY");
                        }
                        break;
                    }
                    if (command.equals("DRAW BOARD")) {
                        drawBoards(player);
                    } else {
                        String[] tokens = command.split("\\s+");
                        if (tokens.length != 4 || !tokens[0].equals("PLACE")) {
                            throw new IllegalArgumentException("Use PLACE <ship> <coordinate> H|V");
                        }
                        Orientation orientation;
                        if (tokens[3].equals("H")) orientation = Orientation.HORIZONTAL;
                        else if (tokens[3].equals("V")) orientation = Orientation.VERTICAL;
                        else throw new IllegalArgumentException("Orientation must be H or V");
                        game.placeShip(player, ShipType.valueOf(tokens[1]),
                                Coordinate.parse(tokens[2]), orientation);
                        output.println("Placed " + tokens[1]);
                    }
                } catch (IllegalArgumentException | IllegalStateException e) {
                    output.println("Error: " + e.getMessage());
                }
            }
            output.println("Pass the terminal to the next player. Previous output is not hidden.");
        }
        game.start();
        while (game.getPhase() == BattleshipGame.Phase.IN_PROGRESS) {
            String player = game.getCurrentPlayer();
            String command = readCommand(player);
            if (command == null || command.equals("QUIT")) return;
            try {
                if (command.equals("DRAW BOARD")) {
                    drawBoards(player);
                } else {
                    String[] tokens = command.split("\\s+");
                    if (tokens.length != 2 || !tokens[0].equals("FIRE")) {
                        throw new IllegalArgumentException("Use FIRE <coordinate> or DRAW BOARD");
                    }
                    output.println(player + " fired " + game.fire(player, Coordinate.parse(tokens[1])));
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                output.println("Error: " + e.getMessage());
            }
        }
        output.println("Winner: " + game.getWinner().orElseThrow());
        output.flush();
    }

    private String readCommand(String player) throws IOException {
        output.print(player + "> ");
        output.flush();
        String line = input.readLine();
        if (line == null) {
            output.println("Input ended; game stopped.");
            output.flush();
            return null;
        }
        return line.trim().toUpperCase(Locale.ROOT);
    }

    private void drawBoards(String player) {
        List<String> own = game.getOwnBoard(player);
        List<String> tracking = game.getTrackingBoard(player);
        output.println("    OWN BOARD                 FIRED MISSILES");
        output.println("    1 2 3 4 5 6 7 8 9 10      1 2 3 4 5 6 7 8 9 10");
        for (int row = 0; row < Coordinate.BOARD_SIZE; row++) {
            output.println((char) ('A' + row) + "   " + spaced(own.get(row))
                    + "      " + spaced(tracking.get(row)));
        }
        output.println("S=own ship, X=hit, o=miss, .=untried water");
    }

    private String spaced(String row) {
        return String.join(" ", row.split(""));
    }

    public static void main(String[] args) throws IOException {
        new BattleshipConsole(new BattleshipGame("Player1", "Player2"),
                new BufferedReader(new InputStreamReader(System.in)),
                new PrintWriter(System.out, true)).run();
    }
}
