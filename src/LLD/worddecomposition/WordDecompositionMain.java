package LLD.worddecomposition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** Reads N followed by N words; emits only words with valid decompositions. */
public final class WordDecompositionMain {
    public static void main(String[] args) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String firstLine = input.readLine();
        if (firstLine == null) {
            throw new IllegalArgumentException("Missing word count");
        }
        int count = Integer.parseInt(firstLine.trim());
        if (count < 1 || count > 10_000) {
            throw new IllegalArgumentException("N must be between 1 and 10000");
        }
        List<String> words = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String word = input.readLine();
            if (word == null) {
                throw new IllegalArgumentException("Expected " + count + " words, received " + i);
            }
            words.add(word);
        }
        WordDecomposer decomposer = new WordDecomposer(words);
        PrintWriter output = new PrintWriter(System.out);
        for (String word : words) {
            boolean[] emitted = {false};
            decomposer.forEachDecomposition(word, decomposition -> {
                output.print(emitted[0] ? " | " : word + ": ");
                output.print(decomposition);
                emitted[0] = true;
            });
            if (emitted[0]) {
                output.println();
            }
        }
        output.flush();
        if (output.checkError()) {
            throw new IOException("Failed to write decompositions");
        }
    }
}
