package DSA.Agoda;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/*
 * Problem:
 * Given a chemical formula and a map of element symbols to atomic weights,
 * calculate the total molecular weight.
 *
 * Supported syntax:
 * - An element symbol, such as C, H, or O.
 * - An optional number after an element, such as H2.
 * - Parenthesized groups with an optional multiplier, such as (CH4)2.
 * - Nested groups, such as H((CH4)2)3.
 *
 * Approach:
 * Keep one running sum for each currently open group.
 *
 * - On '(', start a new group by pushing 0.
 * - On an element, parse its following number and add weight * count to the
 *   current group.
 * - On ')', parse its following multiplier, remove the group's sum, multiply
 *   it, and add it to the parent group.
 *
 * The stack avoids recursively parsing substrings and handles arbitrary
 * nesting depth.
 *
 * Time Complexity: O(n), where n is the formula length.
 * Space Complexity: O(n) in the worst case for nested parentheses.
 */
public class ComputeMolecularWeight {

    public static int calculate(String formula, Map<String, Integer> atomicWeights) {
        if (formula == null || formula.isEmpty()
                || atomicWeights == null || atomicWeights.isEmpty()) {
            throw new IllegalArgumentException("Invalid formula or atomic weights");
        }

        Deque<Integer> groupWeights = new ArrayDeque<Integer>();
        groupWeights.push(0);

        int index = 0;
        while (index < formula.length()) {
            char current = formula.charAt(index);

            if (current == '(') {
                // Everything until the matching ')' belongs to this group.
                groupWeights.push(0);
                index++;
            } else if (current == ')') {
                if (groupWeights.size() == 1) {
                    throw new IllegalArgumentException("Unmatched closing parenthesis");
                }

                int groupWeight = groupWeights.pop();
                index++;

                int multiplierStart = index;
                while (index < formula.length()
                        && Character.isDigit(formula.charAt(index))) {
                    index++;
                }
                int multiplier = parseNumber(formula, multiplierStart, index);

                // Add the completed, multiplied group to its parent group.
                groupWeights.push(groupWeights.pop() + groupWeight * multiplier);
            } else if (Character.isUpperCase(current)) {
                String element = String.valueOf(current);
                Integer atomicWeight = atomicWeights.get(element);
                if (atomicWeight == null) {
                    throw new IllegalArgumentException("Unknown element: " + element);
                }

                index++;
                int countStart = index;
                while (index < formula.length()
                        && Character.isDigit(formula.charAt(index))) {
                    index++;
                }
                int count = parseNumber(formula, countStart, index);

                groupWeights.push(groupWeights.pop() + atomicWeight * count);
            } else {
                throw new IllegalArgumentException(
                        "Invalid character at index " + index + ": " + current);
            }
        }

        if (groupWeights.size() != 1) {
            throw new IllegalArgumentException("Unmatched opening parenthesis");
        }
        return groupWeights.pop();
    }

    private static int parseNumber(String formula, int start, int end) {
        if (start == end) {
            return 1;
        }

        int value = 0;
        for (int index = start; index < end; index++) {
            value = value * 10 + formula.charAt(index) - '0';
        }
        if (value == 0) {
            throw new IllegalArgumentException("Multipliers must be positive");
        }
        return value;
    }

    public static void main(String[] args) {
        Map<String, Integer> atomicWeights = new HashMap<String, Integer>();
        atomicWeights.put("C", 12);
        atomicWeights.put("H", 1);
        atomicWeights.put("O", 8);

        System.out.println(calculate("CH4", atomicWeights));     // 16
        System.out.println(calculate("H(CH4)2", atomicWeights)); // 33
    }
}
