package DSA.Apple;

import java.util.Arrays;
import java.util.List;

public class SentenceGenerator {

    public static void generateSentences(List<List<String>> wordGroups) {
        // Build each sentence one category at a time.
        backtrack(wordGroups, 0, new StringBuilder());
    }

    private static void backtrack(List<List<String>> wordGroups, int index, StringBuilder current) {
        // Once one word has been picked from every group, print the complete sentence.
        if (index == wordGroups.size()) {
            System.out.println(current.toString().trim());
            return;
        }

        // Try every word from the current group, then recurse for the next group.
        for (String word : wordGroups.get(index)) {
            int lengthBefore = current.length();
            if (lengthBefore > 0) current.append(" ");
            current.append(word);

            backtrack(wordGroups, index + 1, current);

            // Restore the sentence prefix so the next word choice starts cleanly.
            current.setLength(lengthBefore);
        }
    }

    public static void main(String[] args) {
        List<List<String>> wordGroups = Arrays.asList(
                Arrays.asList("The", "A"),
                Arrays.asList("dog", "cat"),
                Arrays.asList("runs", "walks"),
                Arrays.asList("quickly", "slowly"),
                Arrays.asList(".", "!")
        );

        generateSentences(wordGroups);
    }
}
