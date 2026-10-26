package LLD.worddecomposition;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free tests; run the main method to execute. */
public final class WordDecomposerTest {
    public static void main(String[] args) {
        WordDecomposer first = new WordDecomposer(List.of("apple", "pen", "applepen"));
        assertEquals(List.of("apple pen"), first.decompose("applepen"));
        assertEquals(List.of(), first.decompose("apple"));

        WordDecomposer second = new WordDecomposer(
                List.of("cat", "cats", "dog", "sdog", "dogs", "catsdog", "dogcat", "dogcatsdog"));
        assertEquals(List.of("cat sdog", "cats dog"), second.decompose("catsdog"));
        assertEquals(List.of("dog cat"), second.decompose("dogcat"));
        assertEquals(List.of("dog cat sdog", "dog cats dog", "dog catsdog", "dogcat sdog"),
                second.decompose("dogcatsdog"));

        WordDecomposer repeated = new WordDecomposer(List.of("a", "aa", "aaa", "aaaa"));
        assertEquals(List.of("a a a", "a aa", "aa a"), repeated.decompose("aaa"));
        assertEquals(7, repeated.decompose("aaaa").size());
        assertEquals(List.of(), new WordDecomposer(List.of("alone")).decompose("alone"));
        assertEquals(List.of(), new WordDecomposer(List.of("a", "ab")).decompose("ab"));
        assertEquals(List.of(), repeated.decompose("a"));
        assertEquals(List.of(), repeated.decompose("aaaaaaaaaaaaaaaaaaaaaaaaaaaaab"));

        List<String> streamed = new ArrayList<>();
        repeated.forEachDecomposition("aaaa", streamed::add);
        assertEquals(repeated.decompose("aaaa"), streamed);
        expectInvalid(() -> new WordDecomposer(List.of("a", "a")));
        expectInvalid(() -> new WordDecomposer(List.of("A")));
        expectInvalid(() -> new WordDecomposer(List.of("")));
        expectInvalid(() -> new WordDecomposer(List.of()));
        System.out.println("All word decomposition tests passed");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", received " + actual);
        }
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }
}
