package DSA.Apple;

public class ValidParenthesisStringTest {

    public static void main(String[] args) {
        check("", true);
        check("()", true);
        check("(*)", true);
        check("(*))", true);
        check("((*)", true);
        check("((())", false);
        check("(", false);
        check(")", false);
        check("*", true);
        check("***", true);
        check("*(", false);
        check(")*", false);
        check("())*", false);
        check("(*", true);
        check("*)", true);

        // Exhaustively compare all approaches against explicit star substitution.
        for (int length = 0; length <= 8; length++) {
            enumerate(new char[length], 0);
        }
        System.out.println("All valid-parenthesis-string checks passed.");
    }

    private static void enumerate(char[] input, int index) {
        if (index == input.length) {
            String s = new String(input);
            check(s, validSubstitution(input, 0));
            return;
        }
        for (char c : new char[]{'(', ')', '*'}) {
            input[index] = c;
            enumerate(input, index + 1);
        }
    }

    private static boolean validSubstitution(char[] input, int index) {
        if (index == input.length) {
            int balance = 0;
            for (char c : input) {
                if (c == '(') {
                    balance++;
                } else if (c == ')') {
                    balance--;
                }
                if (balance < 0) {
                    return false;
                }
            }
            return balance == 0;
        }
        if (input[index] != '*') {
            return validSubstitution(input, index + 1);
        }
        for (char replacement : new char[]{'(', ')', ' '}) {
            input[index] = replacement;
            boolean valid = validSubstitution(input, index + 1);
            input[index] = '*';
            if (valid) {
                return true;
            }
        }
        return false;
    }

    private static void check(String s, boolean expected) {
        assertResult(s, "greedy", expected, ValidParenthesisString.checkValidString(s));
        assertResult(s, "backtracking", expected, ValidParenthesisString.checkWithBacktracking(s));
        assertResult(s, "memoization", expected, ValidParenthesisString.checkWithMemoization(s));
        assertResult(s, "DP", expected, ValidParenthesisString.checkWithDynamicProgramming(s));
        assertResult(s, "stacks", expected, ValidParenthesisString.checkWithStacks(s));
    }

    private static void assertResult(String s, String approach, boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError(approach + " failed for \"" + s
                    + "\": expected " + expected + ", got " + actual);
        }
    }
}
