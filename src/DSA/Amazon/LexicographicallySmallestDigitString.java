package DSA.Amazon;

import java.util.ArrayDeque;
import java.util.PriorityQueue;

/*
 * Assumed operation:
 * - Emit x % 10, then update x += originalX.
 * - Emit y % 10, then update y += originalY.
 *
 * The target must occur as a subsequence of the emitted string.
 *
 * Important:
 * The original statement does not define a finite bound on operations. Since
 * both digit streams are periodic, "lexicographically smallest" alone may not
 * exist. For example, repeated cycles can create an infinite descending
 * sequence of valid finite strings. This implementation uses the standard
 * well-defined interpretation:
 *
 *   shortest valid string, and lexicographically smallest among those.
 *
 * State:
 * (xPosition, yPosition, matchedPrefixLength)
 *
 * Only positions modulo each stream's digit period are needed.
 *
 * Time Complexity: O(Px * Py * L)
 * Space Complexity: O(Px * Py * L)
 * where L is target.length() and Px/Py are at most 10.
 */
public class LexicographicallySmallestDigitString {

    private static class State {
        private final int xPosition;
        private final int yPosition;
        private final int matched;
        private final String result;

        private State(int xPosition, int yPosition, int matched, String result) {
            this.xPosition = xPosition;
            this.yPosition = yPosition;
            this.matched = matched;
            this.result = result;
        }
    }

    public static String find(
            String target, int originalX, int originalY) {
        if (target == null || originalX < 0 || originalY < 0) {
            throw new IllegalArgumentException("Invalid target or starting values");
        }
        if (target.isEmpty()) {
            return "";
        }

        int xPeriod = period(originalX);
        int yPeriod = period(originalY);
        boolean[][][] visited = new boolean[xPeriod][yPeriod][target.length() + 1];
        PriorityQueue<State> queue = new PriorityQueue<State>((first, second) -> {
            int byLength = Integer.compare(
                    first.result.length(), second.result.length());
            return byLength != 0
                    ? byLength
                    : first.result.compareTo(second.result);
        });
        queue.offer(new State(0, 0, 0, ""));
        visited[0][0][0] = true;

        while (!queue.isEmpty()) {
            State current = queue.poll();
            if (current.matched == target.length()) {
                return current.result;
            }

            // The queue processes paths by length, then lexicographically.
            // Therefore, the first goal state is the shortest and then smallest.
            State nextFromX = advance(
                    current, true, originalX, xPeriod, yPeriod, target);
            State nextFromY = advance(
                    current, false, originalY, xPeriod, yPeriod, target);

            for (State next : new State[]{nextFromX, nextFromY}) {
                if (next == null || visited[next.xPosition][next.yPosition][next.matched]) {
                    continue;
                }
                visited[next.xPosition][next.yPosition][next.matched] = true;
                queue.offer(next);
            }
        }

        throw new IllegalArgumentException("Target cannot be formed");
    }

    private static State advance(
            State state,
            boolean useX,
            int original,
            int xPeriod,
            int yPeriod,
            String target) {
        int position = useX ? state.xPosition : state.yPosition;
        int digit = (int) (((long) original * (position + 1)) % 10);
        int matched = state.matched;

        if (digit == target.charAt(matched) - '0') {
            matched++;
        }

        int nextX = useX ? (state.xPosition + 1) % xPeriod : state.xPosition;
        int nextY = useX ? state.yPosition : (state.yPosition + 1) % yPeriod;

        return new State(
                nextX, nextY, matched, state.result + digit);
    }

    private static int period(int value) {
        int digit = value % 10;
        return 10 / gcd(digit, 10);
    }

    private static int gcd(int first, int second) {
        while (second != 0) {
            int remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    public static void main(String[] args) {
        System.out.println(find("579", 1, 5)); // 36924 under the assumption
    }
}
