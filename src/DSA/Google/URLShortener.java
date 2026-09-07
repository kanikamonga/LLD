package DSA.Google;

import java.util.*;

/**
 * Unique String Generator with Character Frequency Threshold
 *
 * Generates unique lowercase strings where each character appears at most T times.
 * Shorter strings are generated before longer strings (lexicographic within each length).
 *
 * Approach:
 *   Maintain the current string and a frequency array [26].
 *   To get the next string:
 *   1. Try to increment from the rightmost position to a valid next character.
 *   2. Greedily fill remaining positions with the smallest valid characters.
 *   3. If no valid string exists at the current length, increase length.
 *
 * ==================== TIME & SPACE COMPLEXITY ====================
 *
 * Let L = current string length, T = threshold, A = alphabet size (26)
 *     MaxLen = A * T (maximum possible string length)
 *
 * ┌──────────────────┬────────────────────┬───────────────────────────────────────────────────┐
 * │ Operation        │ Time Complexity    │ Explanation                                       │
 * ├──────────────────┼────────────────────┼───────────────────────────────────────────────────┤
 * │ getNext()        │ O(L * A) amortized │ Backtrack at most L positions, each tries up to   │
 * │                  │                    │ A characters. Greedy fill is O(L * A) worst case. │
 * ├──────────────────┼────────────────────┼───────────────────────────────────────────────────┤
 * │ Constructor      │ O(1)               │ Just initializes threshold and state.             │
 * └──────────────────┴────────────────────┴───────────────────────────────────────────────────┘
 *
 * SPACE COMPLEXITY: O(L)
 *   - current char array → O(L)
 *   - freq array         → O(A) = O(26) = O(1)
 *
 * =================================================================
 */
class URLShortener {

    private final int threshold;
    private int currentLength;
    private char[] current;
    private final int[] freq;
    private boolean firstCall;

    public URLShortener(int threshold) {
        this.threshold = threshold;
        this.currentLength = 1;
        this.current = null;
        this.freq = new int[26];
        this.firstCall = true;
    }

    public String getNext() {
        if (firstCall) {
            firstCall = false;
            current = new char[currentLength];
            current[0] = 'a';
            freq[0] = 1;
            return new String(current);
        }

        if (advance()) {
            return new String(current, 0, currentLength);
        }
        return null;
    }

    /**
     * Advances to the next valid string in lexicographic order.
     * Backtracks from the rightmost position, trying the next valid character.
     * If the current length is exhausted, moves to the next length.
     */
    private boolean advance() {
        int pos = currentLength - 1;

        while (pos >= 0) {
            // Remove current char at this position from frequency
            freq[current[pos] - 'a']--;

            // Try the next valid characters at this position
            for (char c = (char) (current[pos] + 1); c <= 'z'; c++) {
                if (freq[c - 'a'] < threshold) {
                    current[pos] = c;
                    freq[c - 'a']++;
                    if (fillRemaining(pos + 1)) {
                        return true;
                    }
                    freq[c - 'a']--;
                }
            }
            pos--;
        }

        // All strings of currentLength exhausted — move to next length
        currentLength++;
        if (currentLength > 26 * threshold) {
            return false;
        }
        current = new char[currentLength];
        Arrays.fill(freq, 0);
        return fillRemaining(0);
    }

    /**
     * Greedily fills positions [startPos, currentLength) with the smallest
     * valid character at each position. Returns false if no valid fill exists.
     */
    private boolean fillRemaining(int startPos) {
        for (int i = startPos; i < currentLength; i++) {
            boolean found = false;
            for (char c = 'a'; c <= 'z'; c++) {
                if (freq[c - 'a'] < threshold) {
                    current[i] = c;
                    freq[c - 'a']++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                // Undo fills from startPos to i-1
                for (int j = startPos; j < i; j++) {
                    freq[current[j] - 'a']--;
                }
                return false;
            }
        }
        return true;
    }

    // ---- Demo ----

    public static void main(String[] args) {
        // Test 1: threshold = 2, generate 28 strings
        System.out.println("=== Threshold = 2, first 28 strings ===");
        URLShortener gen1 = new URLShortener(2);
        List<String> results1 = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            results1.add(gen1.getNext());
        }
        System.out.println(results1);
        assert results1.get(0).equals("a") : "First should be 'a'";
        assert results1.get(25).equals("z") : "26th should be 'z'";
        assert results1.get(26).equals("aa") : "27th should be 'aa'";
        assert results1.get(27).equals("ab") : "28th should be 'ab'";

        // Test 2: threshold = 1, "aa" must be skipped
        System.out.println("\n=== Threshold = 1, first 28 strings ===");
        URLShortener gen2 = new URLShortener(1);
        List<String> results2 = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            results2.add(gen2.getNext());
        }
        System.out.println(results2);
        assert results2.get(25).equals("z") : "26th should be 'z'";
        assert results2.get(26).equals("ab") : "27th should be 'ab' (aa is invalid)";
        assert results2.get(27).equals("ac") : "28th should be 'ac'";

        // Test 3: threshold = 1, valid length-2 strings = 26 * 25 = 650
        System.out.println("\n=== Threshold = 1, counting length-2 strings ===");
        URLShortener gen3 = new URLShortener(1);
        for (int i = 0; i < 26; i++) gen3.getNext();
        int count = 0;
        String s;
        while ((s = gen3.getNext()) != null && s.length() == 2) {
            count++;
        }
        System.out.println("Length-2 strings with T=1: " + count);
        assert count == 26 * 25 : "Should be 650, got " + count;

        System.out.println("\nAll tests passed.");
    }
}
