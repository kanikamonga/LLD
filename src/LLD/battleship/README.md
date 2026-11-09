# Battleship

Runnable Java 17+ implementation using regular classes, not records. No external
dependencies, UI framework, or database is required.

## Run

From the repository root:

```bash
javac -d out src/LLD/battleship/*.java
java -cp out LLD.battleship.BattleshipConsole
```

For a complete scripted match through the same game loop:

```bash
java -cp out LLD.battleship.BattleshipDemo
java -cp out LLD.battleship.BattleshipTest
```

## Assumptions and rules

- Exactly two distinct, nonblank player names are supplied when constructing
  the game. There is no joining or replacing a player after construction.
- Each player has one 10x10 ocean board and one 10x10 outgoing-shot tracking
  board. Coordinates are rows A-J and columns 1-10.
- Each fleet contains exactly one CARRIER (5), BATTLESHIP (4), CRUISER (3),
  SUBMARINE (3), and DESTROYER (2). Both length-three ships are required.
- Ships are straight, horizontal to the right or vertical downward from their
  starting coordinate. They cannot overlap or extend outside the board.
  Adjacent ships, including diagonal contact, are allowed.
- Placement is atomic on validation failure. Ships cannot be moved or removed,
  and placement is disallowed after the match starts.
- Both fleets must be complete before play. The first constructor player
  fires first. Every valid shot consumes a turn, including hits and sinking
  shots. There is no extra turn for a hit.
- Repeated shots, malformed input, and out-of-turn actions are rejected without
  consuming a turn. Drawing the board also does not consume a turn.
- A shot reports MISS, HIT, or SUNK. A sunk result also identifies the ship
  type. It does not disclose previously unknown ship coordinates.
- Sinking the fifth enemy ship ends the match immediately. Further firing is
  rejected. Fleet variants, AI, persistence, rematches, timers,
  and disconnect adjudication are outside this local implementation.
- QUIT or end-of-input stops the console session without declaring a winner.
- The console uses a shared terminal, so secrecy is by cooperation: placement
  prompts and prior output remain in scrollback. This is not a secure two-user
  interface. The in-process API is trusted; a network client must never have
  unrestricted access to the opponent's own-board view.
- The game synchronizes state operations. Console I/O happens outside its
  monitor. Input is single-threaded; there is no need for background threads
  for a local turn-based game.

## Console input

During setup each player enters, for example:

```text
PLACE CARRIER A1 H
PLACE BATTLESHIP B1 H
PLACE CRUISER C1 H
PLACE SUBMARINE D1 H
PLACE DESTROYER E1 H
DRAW BOARD
READY
```

Repeat for Player2. Commands are case-insensitive. During play:

```text
Fire C5
Draw Board
QUIT
```

Legend: `S` = own unhit ship, `X` = hit, `o` = miss, `.` = untried water.
The right board displays only outgoing missile outcomes, never enemy ship
locations. The final hit on a ship returns SUNK; previous hits stay marked X.

## Object model and responsibilities

```text
BattleshipConsole --> BattleshipGame
                         |
                         +-- owns exactly two Player objects
                                  |
                                  +-- owns OceanBoard --> owns Ship objects
                                  +-- owns TrackingBoard

Coordinate: immutable value object
ShipType / Orientation: fixed fleet specification and placement direction
ShotResult: immutable outcome, with optional sunk ship identity
```

- `BattleshipGame` is the facade and lifecycle/turn coordinator:
  SETUP -> IN_PROGRESS -> FINISHED. It alone connects an attack, the defender's
  board update, the attacker's tracking update, and winner detection.
- `OceanBoard` validates placement, resolves incoming missiles, and detects
  total fleet loss. `Ship` owns its occupied and hit cells.
- `TrackingBoard` records only outgoing shot results.
- `Player` groups its name and two boards without handling input or turns.
- `BattleshipConsole` implements setup and the game loop, command parsing,
  error presentation, and rendering. Reader/writer injection makes the loop
  testable without interactive input. `BattleshipDemo` exercises that loop.
- Mutable boards and ships are package-private and never returned through the
  public game API. Board views are immutable row snapshots.

Composition keeps responsibilities separate without artificial ship
subclasses: ships differ in length, not behavior. No Factory, Singleton, or
Strategy is needed for fixed rules. A future AI input provider or variant
placement rule would justify an abstraction when that variation is introduced.

Public domain API:

```java
BattleshipGame(String firstPlayer, String secondPlayer)
void placeShip(String player, ShipType type, Coordinate start, Orientation orientation)
void start()
ShotResult fire(String player, Coordinate coordinate)
List<String> getOwnBoard(String player)
List<String> getTrackingBoard(String player)
Optional<String> getWinner()
```

Invalid arguments produce `IllegalArgumentException`; invalid lifecycle
operations produce `IllegalStateException`. The console reports these errors
and re-prompts rather than losing the turn. Null required objects are rejected.

## Peer-to-peer extension (design only)

Do not send either fleet to the other peer or replicate the local game object
as-is. Each peer owns its own ocean board and the tracking board for its
outgoing shots. Extract the turn/shot exchange from the local coordinator
behind a transport interface, for example:

```java
interface PeerTransport {
    void send(ProtocolMessage message);
    ProtocolMessage receive();
}
```

Use an authenticated direct connection (TCP/TLS or an authenticated WebRTC data
channel) with these protocol messages:

- HELLO / READY: match ID, protocol and rule versions, player identities, agreed
  first player, and fleet commitments if cheating protection is required.
- FIRE: match ID, turn number, unique shot ID, coordinate.
- RESULT: matching turn/shot IDs and MISS/HIT/SUNK plus sunk ship type.
- ACK / RESUME: acknowledged sequence and replay position.

The attacker transitions from MY_TURN to AWAITING_RESULT and advances only
after a matching, valid RESULT. The defender rejects out-of-turn messages and
atomically caches the response by shot ID before sending it. A retry of the
same shot returns the cached result without applying another hit; reuse of a
shot ID for another coordinate is a protocol error. Both peers retain an
ordered local log and reconcile acknowledged sequence numbers on reconnect.
Unsolicited, duplicated, stale, and mismatched messages must not advance turns.
An unresolved disconnect pauses the match; a timeout/forfeit policy needs
explicit agreement, not an assumption that a timeout means a miss.

Without a trusted server, a dishonest peer can lie about hits or move ships.
A practical extension is a salted Merkle commitment to all 100 cells made
before play; shot responses carry inclusion proofs. Per-cell salts prevent
guessing hidden cells from their hashes. At the end reveal the board and
salts to verify the exact legal fleet and all results; a commitment alone
does not prove at setup that the fleet is legal. Sunk-ship claims need proof
that every cell of that committed ship was hit, or end-game validation.
Early disconnects can still prevent completion/verification.

Direct play without a central server requires an accessible IP/port, manual
port forwarding, LAN access, or successful NAT traversal. Arbitrary Internet
peers behind restrictive NATs cannot be guaranteed to connect without relay
infrastructure. Pairing and key verification can be done out of band.

## Complexity and checks

With the specified fixed 10x10 boards all domain operations are bounded.
Generalized placement is O(ship length), shot lookup is expected O(1), fleet
loss checking is O(number of ships), and rendering is O(board area). Space is
O(board area + total ship cells), including recorded missile outcomes.

The test harness covers coordinate validation, placement rollback, horizontal
and vertical ships, duplicate ships, overlapping ships, turn enforcement,
repeated hit/miss rejection, tracking privacy, immutable snapshots, sinking
each type, victory for either player, post-game rejection, console recovery
from invalid input, drawing, and EOF shutdown.
