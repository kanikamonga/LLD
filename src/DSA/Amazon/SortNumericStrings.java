package DSA.Amazon;

import java.util.Arrays;
import java.util.Comparator;

/*
 * Problem:
 * Sort numeric strings in ascending numerical order without converting them
 * to int, long, float, or double.
 *
 * Representation:
 * - Remove the optional sign.
 * - Remove leading zeroes from the integer part.
 * - Remove trailing zeroes from the fractional part.
 *
 * Comparison:
 * 1. Negative values come before non-negative values.
 * 2. For positive values, compare integer-part lengths and digits.
 * 3. If integer parts are equal, compare fractional digits as if padded with
 *    trailing zeroes.
 * 4. For negative values, reverse the magnitude comparison.
 *
 * Time Complexity: O(n log n * L), where L is the maximum string length.
 * Space Complexity: O(n) for normalized representations.
 */
public class SortNumericStrings {

    private static class NumberParts {
        private final boolean negative;
        private final String integerPart;
        private final String fractionPart;
        private final boolean zero;

        private NumberParts(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Invalid numeric string");
            }

            int start = value.charAt(0) == '-' ? 1 : 0;
            if (start == value.length()) {
                throw new IllegalArgumentException("Invalid numeric string");
            }

            int decimalPoint = value.indexOf('.');
            if (decimalPoint != value.lastIndexOf('.')) {
                throw new IllegalArgumentException("Multiple decimal points");
            }
            if (decimalPoint == 0 || decimalPoint == value.length() - 1) {
                throw new IllegalArgumentException("Invalid decimal format");
            }

            String integer = decimalPoint == -1
                    ? value.substring(start)
                    : value.substring(start, decimalPoint);
            String fraction = decimalPoint == -1
                    ? ""
                    : value.substring(decimalPoint + 1);

            validateDigits(integer);
            if (!fraction.isEmpty()) {
                validateDigits(fraction);
            }

            integer = removeLeadingZeroes(integer);
            fraction = removeTrailingZeroes(fraction);

            this.integerPart = integer;
            this.fractionPart = fraction;
            this.zero = integer.equals("0") && fraction.isEmpty();
            this.negative = value.charAt(0) == '-' && !zero;
        }
    }

    public static void sort(String[] numbers) {
        if (numbers == null) {
            throw new IllegalArgumentException("Array cannot be null");
        }

        Arrays.sort(numbers, new Comparator<String>() {
            @Override
            public int compare(String first, String second) {
                return compareNumbers(new NumberParts(first), new NumberParts(second));
            }
        });
    }

    private static int compareNumbers(NumberParts first, NumberParts second) {
        if (first.negative != second.negative) {
            return first.negative ? -1 : 1;
        }

        int magnitudeComparison = compareMagnitude(first, second);
        return first.negative ? -magnitudeComparison : magnitudeComparison;
    }

    private static int compareMagnitude(NumberParts first, NumberParts second) {
        int byIntegerLength = Integer.compare(
                first.integerPart.length(), second.integerPart.length());
        if (byIntegerLength != 0) {
            return byIntegerLength;
        }

        int byIntegerDigits = first.integerPart.compareTo(second.integerPart);
        if (byIntegerDigits != 0) {
            return byIntegerDigits;
        }

        int maxFractionLength = Math.max(
                first.fractionPart.length(), second.fractionPart.length());
        for (int i = 0; i < maxFractionLength; i++) {
            char firstDigit = digitAt(first.fractionPart, i);
            char secondDigit = digitAt(second.fractionPart, i);
            if (firstDigit != secondDigit) {
                return Character.compare(firstDigit, secondDigit);
            }
        }
        return 0;
    }

    private static char digitAt(String fraction, int index) {
        return index < fraction.length() ? fraction.charAt(index) : '0';
    }

    private static String removeLeadingZeroes(String value) {
        int firstNonZero = 0;
        while (firstNonZero < value.length() - 1
                && value.charAt(firstNonZero) == '0') {
            firstNonZero++;
        }
        return value.substring(firstNonZero);
    }

    private static String removeTrailingZeroes(String value) {
        int lastNonZero = value.length();
        while (lastNonZero > 0 && value.charAt(lastNonZero - 1) == '0') {
            lastNonZero--;
        }
        return value.substring(0, lastNonZero);
    }

    private static void validateDigits(String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing digits");
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                throw new IllegalArgumentException("Invalid numeric string");
            }
        }
    }

    public static void main(String[] args) {
        String[] first = {"1.5", "-2.3", "0.5", "1.05"};
        sort(first);
        System.out.println(Arrays.toString(first));

        String[] second = {"10", "1.1", "1.01", "-1", "-0.5"};
        sort(second);
        System.out.println(Arrays.toString(second));
    }
}
