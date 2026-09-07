package DSA.Amazon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

public class MultipleSubstringPresence {
    private static final int ALPHABET_SIZE = 128;
    private static final int INVALID = -1;

    private static final class TrieNode {
        final int[] next = new int[ALPHABET_SIZE];
        int fail = 0;
        final List<Integer> patternIndexes = new ArrayList<>();

        TrieNode() {
            Arrays.fill(next, INVALID);
        }
    }

    /**
     * LeetCode-style method for checking whether each query string appears
     * anywhere in the large text as a contiguous substring.
     */
    public boolean[] checkSubstrings(String text, String[] queries) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        if (queries == null) {
            throw new IllegalArgumentException("Queries array cannot be null");
        }

        List<TrieNode> trie = new ArrayList<>();
        trie.add(new TrieNode());

        for (int i = 0; i < queries.length; i++) {
            String query = queries[i];
            int current = 0;
            for (int j = 0; j < query.length(); j++) {
                int code = query.charAt(j);
                if (code < 0 || code >= ALPHABET_SIZE) {
                    throw new IllegalArgumentException("Query contains non-ASCII characters");
                }

                int next = trie.get(current).next[code];
                if (next == INVALID) {
                    next = trie.size();
                    trie.add(new TrieNode());
                    trie.get(current).next[code] = next;
                }
                current = next;
            }
            trie.get(current).patternIndexes.add(i);
        }

        buildFailureLinks(trie);

        boolean[] found = new boolean[queries.length];
        int current = 0;
        for (int i = 0; i < text.length(); i++) {
            int code = text.charAt(i);
            if (code < 0 || code >= ALPHABET_SIZE) {
                throw new IllegalArgumentException("Text contains non-ASCII characters");
            }

            while (current != 0 && trie.get(current).next[code] == INVALID) {
                current = trie.get(current).fail;
            }

            int next = trie.get(current).next[code];
            if (next == INVALID) {
                next = 0;
            }
            current = next;

            for (int queryIndex : trie.get(current).patternIndexes) {
                found[queryIndex] = true;
            }
        }

        return found;
    }

    private static void buildFailureLinks(List<TrieNode> trie) {
        Queue<Integer> queue = new ArrayDeque<>();

        for (int code = 0; code < ALPHABET_SIZE; code++) {
            int child = trie.get(0).next[code];
            if (child != INVALID) {
                trie.get(child).fail = 0;
                queue.offer(child);
            }
        }

        while (!queue.isEmpty()) {
            int current = queue.poll();

            for (int code = 0; code < ALPHABET_SIZE; code++) {
                int child = trie.get(current).next[code];
                if (child == INVALID) {
                    continue;
                }

                int fail = trie.get(current).fail;
                while (fail != 0 && trie.get(fail).next[code] == INVALID) {
                    fail = trie.get(fail).fail;
                }

                int fallback = trie.get(fail).next[code];
                trie.get(child).fail = fallback == INVALID ? 0 : fallback;
                trie.get(child).patternIndexes.addAll(
                        trie.get(trie.get(child).fail).patternIndexes);
                queue.offer(child);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String text = reader.readLine();
        if (text == null) {
            return;
        }

        String nextLine = reader.readLine();
        if (nextLine == null) {
            return;
        }

        int count = Integer.parseInt(nextLine.trim());
        String[] queries = new String[count];
        for (int i = 0; i < count; i++) {
            queries[i] = reader.readLine();
        }

        MultipleSubstringPresence solution = new MultipleSubstringPresence();
        boolean[] result = solution.checkSubstrings(text, queries);
        StringBuilder output = new StringBuilder();
        output.append('[');
        for (int i = 0; i < result.length; i++) {
            if (i > 0) {
                output.append(", ");
            }
            output.append(result[i]);
        }
        output.append(']');
        System.out.println(output);
    }
}
