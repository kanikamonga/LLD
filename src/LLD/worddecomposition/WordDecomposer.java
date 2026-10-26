package LLD.worddecomposition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Enumerates dictionary splits containing at least two parts, trying shorter
 * prefixes first. The dictionary is immutable after construction.
 *
 * For a word of length L, suffix feasibility costs O(L^2). Enumeration is
 * output-sensitive and can be exponential: there are up to 2^(L-1) splits.
 * Results are streamed, so the traversal needs only O(L) auxiliary space.
 */
public final class WordDecomposer {
    private static final class TrieNode {
        private final TrieNode[] children = new TrieNode[26];
        private boolean terminal;
    }

    private final TrieNode root = new TrieNode();
    

    public WordDecomposer(List<String> words) {
        Objects.requireNonNull(words, "words");
        if (words.isEmpty() || words.size() > 10_000) {
            throw new IllegalArgumentException("Dictionary must contain 1 to 10000 words");
        }
        Set<String> unique = new HashSet<>();
        int totalLength = 0;
        for (String word : words) {
            validateWord(word);
            if (!unique.add(word)) {
                throw new IllegalArgumentException("Duplicate word: " + word);
            }
            totalLength += word.length();
            if (totalLength > 200_000) {
                throw new IllegalArgumentException("Total word length exceeds 200000");
            }
            TrieNode node = root;
            for (int i = 0; i < word.length(); i++) {
                int index = word.charAt(i) - 'a';
                if (node.children[index] == null) {
                    node.children[index] = new TrieNode();
                }
                node = node.children[index];
            }
            node.terminal = true;
        }
    }

    /** Convenient collecting API; memory usage includes all generated results. */
    public List<String> decompose(String word) {
        List<String> results = new ArrayList<>();
        forEachDecomposition(word, results::add);
        return List.copyOf(results);
    }

    /** Streams splits without retaining potentially exponential output. */
    public void forEachDecomposition(String word, Consumer<String> consumer) {
        validateWord(word);
        Objects.requireNonNull(consumer, "consumer");
        boolean[] canSplit = suffixFeasibility(word);
        enumerate(word, 0, canSplit, new ArrayList<>(), consumer);
    }

    private boolean[] suffixFeasibility(String word) {
        boolean[] possible = new boolean[word.length() + 1];
        possible[word.length()] = true;
        for (int start = word.length() - 1; start >= 0; start--) {
            TrieNode node = root;
            for (int end = start; end < word.length(); end++) {
                node = node.children[word.charAt(end) - 'a'];
                if (node == null) {
                    break;
                }
                if (node.terminal && possible[end + 1]) {
                    possible[start] = true;
                    break;
                }
            }
        }
        return possible;
    }

    private void enumerate(String word, int start, boolean[] canSplit,
                           List<String> parts, Consumer<String> consumer) {
        if (start == word.length()) {
            consumer.accept(String.join(" ", parts));
            return;
        }
        TrieNode node = root;
        for (int end = start; end < word.length(); end++) {
            node = node.children[word.charAt(end) - 'a'];
            if (node == null) {
                break;
            }
            // Exclude the original word as a single-part "decomposition".
            if (start == 0 && end == word.length() - 1) {
                continue;
            }
            if (node.terminal && canSplit[end + 1]) {
                parts.add(word.substring(start, end + 1));
                enumerate(word, end + 1, canSplit, parts, consumer);
                parts.remove(parts.size() - 1);
            }
        }
    }

    private static void validateWord(String word) {
        if (word == null || word.isEmpty() || word.length() > 30) {
            throw new IllegalArgumentException("Words must have length 1 to 30");
        }
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) < 'a' || word.charAt(i) > 'z') {
                throw new IllegalArgumentException("Words must contain only lowercase English letters");
            }
        }
    }
}
