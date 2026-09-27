package DSA.Atlassian;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/*
 * ========================== PROBLEM DESCRIPTION ==========================
 *
 * Design and implement a data structure that supports the following operations
 * in amortized O(1) time:
 *
 * save(int num):
 *   Stores an integer num in the data structure.
 *
 * best():
 *   Returns the most frequent number currently in the data structure and removes
 *   one instance of it. If multiple numbers have the same frequency, return the
 *   one that was entered most recently among those tied numbers.
 *
 * Example:
 *   save(2), save(5), save(8), save(2), save(8), save(9), save(2)
 *
 *   best() -> 2
 *   best() -> 8
 *   best() -> 2
 *   best() -> 9
 *
 * Constraints:
 *   - save and best operations are called at most 10^5 times.
 *   - num is between 1 and 10^9.
 *
 * ========================== SOLUTION APPROACH =============================
 *
 * This is a frequency stack.
 *
 * Maintain:
 *   1. frequency:
 *        num -> current frequency of num.
 *
 *   2. frequencyToNumbers:
 *        freq -> stack of numbers that reached this frequency, in insertion order.
 *
 *   3. maxFrequency:
 *        highest frequency currently present in the data structure.
 *
 * save(num):
 *   - Increase num's frequency.
 *   - Push num into the stack for its new frequency.
 *   - Update maxFrequency.
 *
 * best():
 *   - Pop from the stack at maxFrequency.
 *   - That top element is both:
 *       a. one of the most frequent numbers, and
 *       b. the most recently inserted among that frequency group.
 *   - Decrease its frequency.
 *   - If the maxFrequency stack becomes empty, reduce maxFrequency.
 *
 * Complexity:
 *   save(): O(1) amortized
 *   best(): O(1) amortized
 *   Space: O(N), where N is the number of saved elements not yet removed.
 *
 * =========================================================================
 */
public class MostFrequentNumberStack {
    private final Map<Integer, Integer> frequency;
    private final Map<Integer, Deque<Integer>> frequencyToNumbers;
    private int maxFrequency;

    public MostFrequentNumberStack() {
        this.frequency = new HashMap<Integer, Integer>();
        this.frequencyToNumbers = new HashMap<Integer, Deque<Integer>>();
        this.maxFrequency = 0;
    }

    public void save(int num) {
        int newFrequency = frequency.getOrDefault(num, 0) + 1;
        frequency.put(num, newFrequency);

        frequencyToNumbers
                .computeIfAbsent(newFrequency, key -> new ArrayDeque<Integer>())
                .push(num);

        maxFrequency = Math.max(maxFrequency, newFrequency);
    }

    public int best() {
        if (maxFrequency == 0) {
            throw new NoSuchElementException("No numbers are available");
        }

        Deque<Integer> mostFrequentNumbers = frequencyToNumbers.get(maxFrequency);
        int bestNumber = mostFrequentNumbers.pop();

        int updatedFrequency = frequency.get(bestNumber) - 1;
        if (updatedFrequency == 0) {
            frequency.remove(bestNumber);
        } else {
            frequency.put(bestNumber, updatedFrequency);
        }

        if (mostFrequentNumbers.isEmpty()) {
            frequencyToNumbers.remove(maxFrequency);
            maxFrequency--;
        }

        return bestNumber;
    }

    public static void main(String[] args) {
        MostFrequentNumberStack stack = new MostFrequentNumberStack();

        stack.save(2);
        stack.save(5);
        stack.save(8);
        stack.save(2);
        stack.save(8);
        stack.save(9);
        stack.save(2);

        System.out.println(stack.best()); // 2
        System.out.println(stack.best()); // 8
        System.out.println(stack.best()); // 2
        System.out.println(stack.best()); // 9
    }
}
