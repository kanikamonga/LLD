package DSA.Google;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
Problem: Check if a given string can be converted to another by given possible swaps

Given two strings str1 and str2, check whether str1 can be converted to str2
using the following operations any number of times:
1. Swap any two characters of str1.
2. Swap all occurrences of one distinct character of str1 with all occurrences
   of another distinct character of str1.

Examples:
str1 = "xyyzzlll", str2 = "yllzzxxx"
Output: true

str1 = "xyyzzavl", str2 = "yllzzvac"
Output: false

Explanation:
Swapping any two characters means the order of characters does not matter.
Only character frequencies matter.

Swapping all occurrences of two characters means frequencies can be reassigned
between existing characters. For example, if x appears once and l appears three
times, a global swap can make x appear three times and l appear once.

Therefore, conversion is possible only if:
1. Both strings have the same length.
2. Both strings contain the same set of distinct characters.
3. The multiset of character frequencies is the same in both strings.

Time Complexity: O(n + k log k), where n is string length and k is the number
of distinct characters.
Space Complexity: O(k).
*/
public class StringConversionBySwaps {

    public static boolean canConvert(String str1, String str2) {
        if (str1 == null || str2 == null || str1.length() != str2.length()) {
            return false;
        }

        Map<Character, Integer> freq1 = buildFrequencyMap(str1);
        Map<Character, Integer> freq2 = buildFrequencyMap(str2);

        if (!freq1.keySet().equals(freq2.keySet())) {
            return false;
        }

        List<Integer> counts1 = new ArrayList<Integer>(freq1.values());
        List<Integer> counts2 = new ArrayList<Integer>(freq2.values());

        Collections.sort(counts1);
        Collections.sort(counts2);

        return counts1.equals(counts2);
    }

    private static Map<Character, Integer> buildFrequencyMap(String str) {
        Map<Character, Integer> frequency = new HashMap<Character, Integer>();

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            Integer count = frequency.get(ch);
            frequency.put(ch, count == null ? 1 : count + 1);
        }

        return frequency;
    }

    public static void main(String[] args) {
        System.out.println(canConvert("xyyzzlll", "yllzzxxx"));
        System.out.println(canConvert("xyyzzavl", "yllzzvac"));
    }
}
