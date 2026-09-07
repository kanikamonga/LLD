package DSA.Apple;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/*
Problem: Count Visible People with Observer-Height Transparency

Given an array heights, return an array visible where visible[i] is the number
of people to the left of index i that person i can see.

Person i can see person j, where j < i, if every person k between them
(j < k < i) does not block the view. A middle person k blocks the view only
when both conditions are true:
1. heights[k] >= heights[i]      // k is at least as tall as the observer
2. heights[k] >= heights[j]      // k is at least as tall as the target

So, a middle person shorter than the observer is transparent. A middle person
taller than the observer is still transparent for targets taller than that
middle person.

Example:
heights = [1, 10, 6, 7, 9, 2, 4, 5]
output  = [0, 1, 1, 2, 3, 2, 3, 4]

Note: This follows the stated blocker rule. The commonly shown sample
[0, 1, 1, 2, 3, 1, 2, 4] conflicts with that rule for indices 5 and 6.

Approach:
For each observer i, visible targets on the left fall into two groups:
1. People after the nearest previous height >= heights[i].
   Everyone in this range is shorter than the observer, so they are transparent
   to each other and all are visible.
2. Taller/equal "record" people before that nearest blocker.
   Among taller/equal people, only suffix maximums from right to left can be
   visible, because a taller/equal person hides shorter/equal targets behind it.

Two monotonic stacks maintain these counts in O(n):
- previousGreaterOrEqual finds the nearest previous height >= current height.
- rightToLeftRecords keeps visible taller/equal suffix records from the left
  side after removing heights shorter than the current observer.

Time Complexity: O(n), because each index is pushed and popped at most once.
Space Complexity: O(n).
*/
public class CountVisiblePeopleWithObserverHeightTransparency {

    public static int[] countVisiblePeople(int[] heights) {
        int n = heights.length;
        int[] visible = new int[n];

        Deque<Integer> previousGreaterOrEqual = new ArrayDeque<Integer>();
        Deque<Integer> rightToLeftRecords = new ArrayDeque<Integer>();

        for (int i = 0; i < n; i++) {
            int height = heights[i];

            while (!previousGreaterOrEqual.isEmpty()
                    && heights[previousGreaterOrEqual.peek()] < height) {
                previousGreaterOrEqual.pop();
            }

            int previousBlocker = previousGreaterOrEqual.isEmpty()
                    ? -1
                    : previousGreaterOrEqual.peek();
            int shorterTransparentPeople = i - previousBlocker - 1;

            while (!rightToLeftRecords.isEmpty()
                    && heights[rightToLeftRecords.peek()] < height) {
                rightToLeftRecords.pop();
            }

            int tallerVisiblePeople = rightToLeftRecords.size();
            visible[i] = shorterTransparentPeople + tallerVisiblePeople;

            previousGreaterOrEqual.push(i);

            if (!rightToLeftRecords.isEmpty()
                    && heights[rightToLeftRecords.peek()] == height) {
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
