package DSA.Google;

/*
 * ========================== PROBLEM DESCRIPTION ==========================
 *
 * 4485. Babylon Game: Predict the Winner with Perfect Play
 * Difficulty: Hard | Tags: Game Theory, Recursion, Memoization, DP
 *
 * Babylon is a two-player alternating-moves game played with 12 tiles.
 * There are 4 colors with 3 tiles each. Initially, 12 separate stacks of height 1.
 *
 * A move: pick up one stack and place it ON TOP of another stack.
 * Valid if:
 *   - Both stacks have the SAME HEIGHT, OR
 *   - Both stacks have the SAME TOP COLOR.
 *
 * The player who cannot move loses (normal play convention: last to move wins).
 * Determine who wins with perfect play: "First" or "Second".
 *
 * Input:  String[] of 12 tile colors (4 unique colors, 3 each).
 * Output: "First" or "Second".
 *
 * Example:
 *   Input:  ["Red","Red","Red","Blue","Blue","Blue","Green","Green","Green","Yellow","Yellow","Yellow"]
 *   Output: "First"
 *
 * ========================== SOLUTION APPROACH =============================
 *
 * 1. STATE REPRESENTATION
 *    Each stack is characterized by (height, topColor). The game state is the
 *    multiset (unordered collection) of all such pairs.
 *    We encode it as a canonically sorted string for HashMap-based memoization.
 *
 * 2. MOVE GENERATION
 *    For every ordered pair (i, j) of distinct stacks:
 *      - Check validity: height_i == height_j OR topColor_i == topColor_j.
 *      - If valid, merge: remove both, add new stack (height_i + height_j, topColor_i).
 *        (Stack i is placed ON TOP of j, so its color becomes the new top.)
 *      - Note: (i on j) and (j on i) are DIFFERENT moves (different resulting top color),
 *        so both directions must be explored.
 *
 * 3. MINIMAX WITH MEMOIZATION
 *    canWin(state):
 *      - If no valid move exists → current player loses → return false.
 *      - If ANY move leads to a state where the opponent CANNOT win → return true.
 *      - Otherwise → return false.
 *    Memoize on the canonical state string to avoid recomputation.
 *
 * 4. COMPLEXITY
 *    - State space: number of distinct multisets of (height, color) pairs summing
 *      to 12. Bounded by colored partitions of 12 — in practice ~10^4–10^5 states.
 *    - Per state: O(n^2) move generation where n = number of stacks (≤ 12).
 *    - Overall: feasible with memoization.
 *
 * =========================================================================
 */

import java.util.*;

public class BabylonGame {

    private final Map<String, Boolean> memo = new HashMap<>();

    public String solve(String[] tiles) {
        Map<String, Integer> colorMap = new HashMap<>();
        int nextColor = 0;
        List<int[]> stacks = new ArrayList<>();

        for (String tile : tiles) {
            colorMap.putIfAbsent(tile, nextColor++);
            stacks.add(new int[]{1, colorMap.get(tile)});
        }

        memo.clear();
        return canWin(stacks) ? "First" : "Second";
    }

    /**
     * Encode the game state as a canonical sorted string for memoization.
     * E.g. "1,0|1,0|1,0|1,1|1,1|1,1|..." for the initial state.
     */
    private String encode(List<int[]> stacks) {
        List<String> parts = new ArrayList<>(stacks.size());
        for (int[] s : stacks) {
            parts.add(s[0] + "," + s[1]);
        }
        Collections.sort(parts);
        return String.join("|", parts);
    }

    /**
     * Returns true if the current player (whoever's turn it is) has a winning
     * strategy from this state.
     */
    private boolean canWin(List<int[]> stacks) {
        String key = encode(stacks);
        if (memo.containsKey(key)) return memo.get(key);

        int n = stacks.size();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;

                int[] picked = stacks.get(i);  // stack being picked up (goes on top)
                int[] target = stacks.get(j);   // stack being placed onto

                // Validity check
                if (picked[0] != target[0] && picked[1] != target[1]) continue;

                // Build new state: remove i and j, add merged stack
                List<int[]> next = new ArrayList<>(n - 1);
                for (int k = 0; k < n; k++) {
                    if (k != i && k != j) next.add(stacks.get(k));
                }
                next.add(new int[]{picked[0] + target[0], picked[1]});

                // If opponent loses from the resulting state, current player wins
                if (!canWin(next)) {
                    memo.put(key, true);
                    return true;
                }
            }
        }

        // No winning move found (including no moves at all) → current player loses
        memo.put(key, false);
        return false;
    }

    public static void main(String[] args) {
        BabylonGame game = new BabylonGame();

        String[] input1 = {"Red", "Red", "Red", "Blue", "Blue", "Blue",
                           "Green", "Green", "Green", "Yellow", "Yellow", "Yellow"};
        System.out.println("Input: " + Arrays.toString(input1));
        System.out.println("Output: " + game.solve(input1));
    }
}
