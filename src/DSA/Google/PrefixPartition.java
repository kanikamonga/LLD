package DSA.Google;

import java.util.Arrays;

/**
 * Partition an array so that all strings for which isValid() returns true
 * appear at the front, using the minimum number of swaps.
 *
 * isValid(s) returns true iff a hidden secret prefix P is a prefix of s.
 *
 * Approach — Two-pointer partition (like Lomuto/Dutch-flag):
 *   - left pointer scans from start, right pointer scans from end.
 *   - left finds the first invalid element, right finds the last valid element.
 *   - Swap them. Repeat until pointers cross.
 *   - This guarantees exactly K swaps in the worst case, where K = number of
 *     valid strings that were originally in the "wrong" half.
 *
 * ==================== TIME & SPACE COMPLEXITY ====================
 *
 * Let N = array length, L = max length of secret prefix P,
 *     M = max length of a string in arr.
 *
 * ┌──────────────────┬──────────────────────┬──────────────────────────────────────────┐
 * │ Operation        │ Complexity           │ Explanation                              │
 * ├──────────────────┼──────────────────────┼──────────────────────────────────────────┤
 * │ isValid(s)       │ O(min(L, len(s)))    │ Compares prefix P char-by-char against s │
 * ├──────────────────┼──────────────────────┼──────────────────────────────────────────┤
 * │ partition()      │ O(N * L)             │ Each of N elements checked once by       │
 * │                  │                      │ isValid; each call costs O(L).           │
 * ├──────────────────┼──────────────────────┼──────────────────────────────────────────┤
 * │ Swaps performed  │ At most min(V, N-V)  │ V = count of valid strings. Only         │
 * │                  │                      │ misplaced elements are swapped.           │
 * └──────────────────┴──────────────────────┴──────────────────────────────────────────┘
 *
 * SPACE COMPLEXITY: O(1) — in-place, no auxiliary data structures.
 *
 * =================================================================
 */
class PrefixPartition {

    private final String secretPrefix;

    public PrefixPartition(String secretPrefix) {
        this.secretPrefix = secretPrefix;
    }

    /** Simulates the opaque API: returns true iff P is a prefix of s. O(L) */
    private boolean isValid(String s) {
        return s.startsWith(secretPrefix);
    }

    /**
     * Partitions arr in-place so all valid strings come first.
     * Uses two-pointer swap to minimize total swaps.
     *
     * @return the number of swaps performed
     */
    public int partition(String[] arr) {
        int left = 0;
        int right = arr.length - 1;
        int swaps = 0;

        while (left <= right) {
            if (isValid(arr[left])) {
                left++;
            } else if (!isValid(arr[right])) {
                right--;
            } else {
                // arr[left] is invalid, arr[right] is valid → swap
                String temp = arr[left];
                arr[left] = arr[right];
                arr[right] = temp;
                left++;
                right--;
                swaps++;
            }
        }
        return swaps;
    }

    // ---- Demo ----

    public static void main(String[] args) {
        // Test 1: basic example
        System.out.println("=== Test 1: prefix = \"a\" ===");
        String[] arr1 = {"ad", "awe", "cat", "apple", "dog"};
        PrefixPartition pp1 = new PrefixPartition("a");
        int swaps1 = pp1.partition(arr1);
        System.out.println("Result: " + Arrays.toString(arr1));
        System.out.println("Swaps:  " + swaps1);
        // Verify: first 3 elements should all start with "a"
        for (int i = 0; i < 3; i++) {
            assert arr1[i].startsWith("a") : "Index " + i + " should be valid";
        }
        for (int i = 3; i < 5; i++) {
            assert !arr1[i].startsWith("a") : "Index " + i + " should be invalid";
        }

        // Test 2: all valid
        System.out.println("\n=== Test 2: all valid ===");
        String[] arr2 = {"apple", "ant", "arc"};
        PrefixPartition pp2 = new PrefixPartition("a");
        int swaps2 = pp2.partition(arr2);
        System.out.println("Result: " + Arrays.toString(arr2) + "  Swaps: " + swaps2);
        assert swaps2 == 0 : "No swaps needed when all valid";

        // Test 3: none valid
        System.out.println("\n=== Test 3: none valid ===");
        String[] arr3 = {"cat", "dog", "bat"};
        PrefixPartition pp3 = new PrefixPartition("z");
        int swaps3 = pp3.partition(arr3);
        System.out.println("Result: " + Arrays.toString(arr3) + "  Swaps: " + swaps3);
        assert swaps3 == 0 : "No swaps needed when none valid";

        // Test 4: longer prefix
        System.out.println("\n=== Test 4: prefix = \"app\" ===");
        String[] arr4 = {"dog", "apple", "cat", "application", "ape", "approve"};
        PrefixPartition pp4 = new PrefixPartition("app");
        int swaps4 = pp4.partition(arr4);
        System.out.println("Result: " + Arrays.toString(arr4) + "  Swaps: " + swaps4);
        for (int i = 0; i < 3; i++) {
            assert arr4[i].startsWith("app") : "Index " + i + " should start with 'app'";
        }

        // Test 5: single element
        System.out.println("\n=== Test 5: single element ===");
        String[] arr5 = {"hello"};
        PrefixPartition pp5 = new PrefixPartition("h");
        pp5.partition(arr5);
        System.out.println("Result: " + Arrays.toString(arr5));
        assert arr5[0].equals("hello");

        System.out.println("\nAll tests passed.");
    }
}
