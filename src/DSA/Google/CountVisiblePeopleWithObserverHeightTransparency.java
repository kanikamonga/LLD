package DSA.Google;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;

/*
 * Problem:
 * Given heights of people standing in a line, count for every person i how
 * many people to the left of i are visible.
 *
 * A person k between target j and observer i blocks the target only when:
 *   heights[k] >= heights[i] && heights[k] >= heights[j]
 *
 * Therefore, people shorter than the observer are transparent. Among people
 * at least as tall as the observer, only right-to-left record heights remain
 * visible.
 *
 * Example:
 *   heights = [1, 10, 6, 7, 9, 2, 4, 5]
 *
 * The written blocking rule produces:
 *   [0, 1, 1, 2, 3, 2, 3, 4]
 *
 * The commonly listed output [0, 1, 1, 2, 3, 1, 2, 4] is inconsistent with
 * the rule: for example, height 2 at index 5 can see height 10 at index 1
 * because every person between them is shorter than height 10.
 *
 * Approach:
 * For the current observer height h:
 * 1. previousGreaterOrEqual finds the closest person on the left whose height
 *    is at least h. Every person after that index is shorter than h and visible.
 * 2. rightToLeftRecords stores suffix maximums of the prefix. Removing records
 *    shorter than h leaves exactly the taller/equal visible targets.
 *
 * Each index is pushed and popped at most once from each stack.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 */
public class CountVisiblePeopleWithObserverHeightTransparency {

    public static int[] countVisiblePeople(int[] heights) {
        int[] visible = new int[heights.length];

        // Monotonic stack used to find the nearest previous height >= current.
        Deque<Integer> previousGreaterOrEqual = new ArrayDeque<Integer>();

        // Suffix maximum records for the already processed prefix.
        Deque<Integer> rightToLeftRecords = new ArrayDeque<Integer>();

        for (int i = 0; i < heights.length; i++) {
            int currentHeight = heights[i];

            // Remove shorter people: they cannot be the nearest >= current.
            while (!previousGreaterOrEqual.isEmpty()
                    && heights[previousGreaterOrEqual.peek()] < currentHeight) {
                previousGreaterOrEqual.pop();
            }

            int previousBlocker = previousGreaterOrEqual.isEmpty()
                    ? -1
                    : previousGreaterOrEqual.peek();

            // All people after the nearest blocker are shorter than the
            // observer, so none of them can block one another.
            int shorterVisible = i - previousBlocker - 1;

            // Retain only records that are at least as tall as the observer.
            while (!rightToLeftRecords.isEmpty()
                    && heights[rightToLeftRecords.peek()] < currentHeight) {
                rightToLeftRecords.pop();
            }

            int tallerVisible = rightToLeftRecords.size();
            visible[i] = shorterVisible + tallerVisible;

            previousGreaterOrEqual.push(i);

            // Equal records are represented only by the nearest occurrence:
            // the nearer equal person blocks an equal person behind it.
            if (!rightToLeftRecords.isEmpty()
                    && heights[rightToLeftRecords.peek()] == currentHeight) {
                rightToLeftRecords.pop();
            }
            rightToLeftRecords.push(i);
        }

        return visible;
    }

    public static void main(String[] args) {
        int[] heights = {1, 10, 6, 7, 9, 2, 4, 5};
        System.out.println(Arrays.toString(countVisiblePeople(heights)));
    }
}
