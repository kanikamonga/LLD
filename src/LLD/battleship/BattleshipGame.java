package LLD.battleship;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Facade and turn coordinator. Domain mutations are atomic under the game monitor. */
public final class BattleshipGame {
    public enum Phase { SETUP, IN_PROGRESS, FINISHED }

    private final List<Player> players;
    private Phase phase = Phase.SETUP;
    private int currentPlayer;
    private String winner;

    public BattleshipGame(String firstPlayer, String secondPlayer) {
        Player first = new Player(firstPlayer);
        Player second = new Player(secondPlayer);
        if (firstPlayer.equals(secondPlayer)) {
            throw new IllegalArgumentException("Players must have distinct names");
        }
        players = List.of(first, second);
    }

    public synchronized void placeShip(String playerName, ShipType type,
                                       Coordinate start, Orientation orientation) {
        requirePhase(Phase.SETUP);
        player(playerName).getOcean().place(type, start, orientation);
    }

    public synchronized boolean isReady(String playerName) {
        return player(playerName).getOcean().isReady();
    }

    public synchronized void start() {
        requirePhase(Phase.SETUP);
        if (players.stream().anyMatch(player -> !player.getOcean().isReady())) {
            throw new IllegalStateException("Both players must place all five ships");
        }
        phase = Phase.IN_PROGRESS;
    }

    public synchronized ShotResult fire(String playerName, Coordinate coordinate) {
        requirePhase(Phase.IN_PROGRESS);
        Objects.requireNonNull(coordinate, "coordinate");
        Player shooter = player(playerName);
        if (shooter != players.get(currentPlayer)) {
            throw new IllegalStateException("Not " + playerName + "'s turn");
        }
        if (shooter.getTracking().hasShot(coordinate)) {
            throw new IllegalArgumentException("Coordinate already targeted: " + coordinate);
        }
        Player opponent = players.get(1 - currentPlayer);
        ShotResult result = opponent.getOcean().receiveShot(coordinate);
        shooter.getTracking().record(result);
        if (opponent.getOcean().allSunk()) {
            winner = shooter.getName();
            phase = Phase.FINISHED;
        } else {
            currentPlayer = 1 - currentPlayer;
        }
        return result;
    }

    public synchronized String getCurrentPlayer() {
        requirePhase(Phase.IN_PROGRESS);
        return players.get(currentPlayer).getName();
    }

    public synchronized Phase getPhase() {
        return phase;
    }

    public synchronized Optional<String> getWinner() {
        return Optional.ofNullable(winner);
    }

    public synchronized List<String> getOwnBoard(String playerName) {
        return player(playerName).getOcean().renderOwnBoard();
    }

    public synchronized List<String> getTrackingBoard(String playerName) {
        return player(playerName).getTracking().render();
    }

    public List<String> getPlayerNames() {
        return players.stream().map(Player::getName).toList();
    }

    private Player player(String name) {
        return players.stream().filter(player -> player.getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player: " + name));
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Expected " + expected + ", game is " + phase);
        }
    }
}
