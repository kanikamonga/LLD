package DSA.Apple;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;

public class ValidParenthesisString {

    /*
     * Greedy: track the minimum and maximum possible unmatched opening counts.
     * For '*', the minimum treats it as ')' and the maximum treats it as '('.
     * Clamp the minimum to zero: negative balances are not valid possibilities.
     * The reachable nonnegative balances form a continuous interval, so keeping
     * its endpoints is sufficient instead of tracking every interpretation.
     *
     * Time: O(n). Auxiliary space: O(1).
     */
    public static boolean checkValidString(String s) {
        Objects.requireNonNull(s, "s cannot be null");
        int minOpen = 0;
        int maxOpen = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                minOpen++;
                maxOpen++;
            } else if (c == ')') {
                minOpen--;
                maxOpen--;
            } else {
                minOpen--;
                maxOpen++;
            }

            // Even using every earlier '*' as '(' cannot match this ')'.
            if (maxOpen < 0) {
                return false;
            }
            minOpen = Math.max(0, minOpen);
        }

        return minOpen == 0;
    }

    /*
     * Backtracking: try all three meanings of each '*'.
     * State: current index and number of unmatched opening parentheses.
     * Reject negative balances and balances too large for the remaining suffix.
     *
     * Time: O(3^n) worst-case upper bound; with w stars, O(n * 3^w)
     * is a more input-sensitive upper bound. Short-circuiting/pruning may help.
     * Auxiliary space: O(n) for recursion, not O(3^n), since branches run serially.
     */
    public static boolean checkWithBacktracking(String s) {
        Objects.requireNonNull(s, "s cannot be null");
        return backtrack(s, 0, 0);
    }

    private static boolean backtrack(String s, int index, int open) {
        if (open < 0 || open > s.length() - index) {
            return false;
        }
        if (index == s.length()) {
            return open == 0;
        }

        char c = s.charAt(index);
        if (c == '(') {
            return backtrack(s, index + 1, open + 1);
        }
        if (c == ')') {
            return backtrack(s, index + 1, open - 1);
        }
        return backtrack(s, index + 1, open)
                || backtrack(s, index + 1, open + 1)
                || backtrack(s, index + 1, open - 1);
    }

    /*
     * Memoization: cache each (index, open) result to avoid repeated subproblems.
     * There are O(n^2) states, each with at most three constant-time transitions.
     * Time: O(n^2). Auxiliary space: O(n^2) cache plus O(n) recursion.
     */
    public static boolean checkWithMemoization(String s) {
        Objects.requireNonNull(s, "s cannot be null");
        Boolean[][] memo = new Boolean[s.length()][s.length() + 1];
        return memoized(s, 0, 0, memo);
    }

    private static boolean memoized(String s, int index, int open, Boolean[][] memo) {
        if (open < 0 || open > s.length() - index) {
            return false;
        }
        if (index == s.length()) {
            return open == 0;
        }
        if (memo[index][open] != null) {
            return memo[index][open];
        }

        char c = s.charAt(index);
        boolean valid;
        if (c == '(') {
            valid = memoized(s, index + 1, open + 1, memo);
        } else if (c == ')') {
            valid = memoized(s, index + 1, open - 1, memo);
        } else {
            valid = memoized(s, index + 1, open, memo)
                    || memoized(s, index + 1, open + 1, memo)
                    || memoized(s, index + 1, open - 1, memo);
        }
        memo[index][open] = valid;
        return valid;
    }

    /*
     * Bottom-up DP: reachable[open] records valid balances after a prefix.
     * Only the previous prefix is needed, so reuse two rows.
     * Time: O(n^2). Auxiliary space: O(n).
     * Keeping all prefix rows instead would use O(n^2) space with the same time.
     */
    public static boolean checkWithDynamicProgramming(String s) {
        Objects.requireNonNull(s, "s cannot be null");
        boolean[] reachable = new boolean[s.length() + 1];
        boolean[] next = new boolean[s.length() + 1];
        reachable[0] = true;

        for (int i = 0; i < s.length(); i++) {
            Arrays.fill(next, false);
            char c = s.charAt(i);
            for (int open = 0; open <= i; open++) {
                if (!reachable[open]) {
                    continue;
                }
                if (c == '(' || c == '*') {
                    next[open + 1] = true;
                }
                if ((c == ')' || c == '*') && open > 0) {
                    next[open - 1] = true;
                }
                if (c == '*') {
                    next[open] = true;
                }
            }
            boolean[] previous = reachable;
            reachable = next;
            next = previous;
        }

        return reachable[0];
    }

    /*
     * Two stacks: keep indices of unmatched '(' and available stars.
     * Match ')' with '(' first, preserving flexible stars for later use.
     * Remaining '(' must each have a star to its right to act as ')'.
     * Time: O(n), since each index is pushed/popped at most once.
     * Auxiliary space: O(n).
     */
    public static boolean checkWithStacks(String s) {
        Objects.requireNonNull(s, "s cannot be null");
        Deque<Integer> openings = new ArrayDeque<>();
        Deque<Integer> stars = new ArrayDeque<>();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                openings.push(i);
            } else if (c == '*') {
                stars.push(i);
            } else if (!openings.isEmpty()) {
                openings.pop();
            } else if (!stars.isEmpty()) {
                stars.pop();
            } else {
                return false;
            }
        }

        while (!openings.isEmpty()) {
            if (stars.isEmpty() || stars.pop() < openings.pop()) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        String[] examples = {"()", "(*)", "(*))", "((*)", "((())"};
        for (String s : examples) {
            System.out.println(s + " -> " + checkValidString(s));
        }
    }
}
