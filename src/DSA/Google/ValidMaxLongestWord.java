package DSA.Google;

import java.util.*;

/**
 * You have a dictionary of string, example - [string, sring, sing, wording, ing,ng, g]
 * You need to tell the maximum longest word in the dictionary that is valid.
 * Definiton of valid string , if you remove only one character from the string and it should be in the dictionary and by doing so if you able to reach at the end with length as 1, it will be valid.
 * Example - ( string-> sring->sing->ing->ng->g) [as all the intermediate string are present in the dictionary this will be a valid string and longest lenght is 6]
 */
public class ValidMaxLongestWord {
    public static void main(String[] args) {
        System.out.println(maximumLongestWord(Set.of("string", "sring", "sing", "wording", "ing", "ng", "g")));
    }

    public static int maximumLongestWord(Set<String> words) {
        //DP
        Map<String, Integer> distance = new HashMap<>();
        int max = 0;

        for (String word : words) {
            max = Math.max(max, maximumLongestWordHelper(word, words, distance));
        }

        return max;
    }

    public static int maximumLongestWordHelper(String word, Set<String> words, Map<String, Integer> distance) {
        if (distance.containsKey(word)) {
            return distance.get(word);
        }

        if (!words.contains(word)) {
            return Integer.MIN_VALUE;
        }

        if (word.length() == 1) {
            distance.put(word, 1);
            return 1;
        }

        int max = Integer.MIN_VALUE;

        for (int i = 0; i < word.length(); i++) {
            String str = word.substring(0, i) + word.substring(i + 1);

            max = Math.max(max, maximumLongestWordHelper(str, words, distance));
        }

        if (max != Integer.MIN_VALUE) {
            max++;
        }

        distance.put(word, max);

        return max;
    }
}