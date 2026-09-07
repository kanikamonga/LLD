package DSA.Agoda;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/*
 * Problem:
 * Design a key-value data structure supporting:
 *
 * 1. insert(key, value): Add a key-value pair or update an existing key.
 * 2. delete(key): Remove the key-value pair for a key.
 * 3. getMax(): Return the maximum value currently stored.
 *
 * Approach:
 * - keyToValue is a hash map used to find the value associated with a key.
 * - valueFrequency is an ordered map from each value to the number of keys
 *   currently having that value.
 * - TreeMap keeps distinct values ordered, so its last key is the maximum
 *   value currently present.
 *
 * Frequencies are required because multiple keys may have the same value.
 * When a key is updated or deleted, its old value frequency must be reduced.
 * A value is removed from the ordered map only when its frequency reaches zero.
 *
 * Complexity:
 * insert: O(log N)
 * delete: O(log N)
 * getMax: O(log N)
 * space: O(N)
 */
public class MaxValueMap<K> {

    private final Map<K, Integer> keyToValue = new HashMap<K, Integer>();
    private final TreeMap<Integer, Integer> valueFrequency =
            new TreeMap<Integer, Integer>();

    public void insert(K key, int value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        Integer oldValue = keyToValue.put(key, value);

        // Updating an existing key first removes its contribution to the old
        // value. This is necessary even when oldValue equals the new value.
        if (oldValue != null) {
            removeValueOccurrence(oldValue);
        }

        valueFrequency.put(value, valueFrequency.getOrDefault(value, 0) + 1);
    }

    /**
     * Deletes key and returns true. Returns false when key does not exist.
     */
    public boolean delete(K key) {
        Integer removedValue = keyToValue.remove(key);
        if (removedValue == null) {
            return false;
        }

        removeValueOccurrence(removedValue);
        return true;
    }

    public int getMax() {
        if (valueFrequency.isEmpty()) {
            throw new NoSuchElementException("No values are present");
        }

        // Values, rather than keys from keyToValue, are the TreeMap keys.
        // Therefore, the last key is the greatest value currently stored.
        return valueFrequency.lastKey();
    }

    public int size() {
        return keyToValue.size();
    }

    private void removeValueOccurrence(int value) {
        int frequency = valueFrequency.get(value);

        if (frequency > 1) {
            valueFrequency.put(value, frequency - 1);
            return;
        }

        valueFrequency.remove(value);
    }

    public static void main(String[] args) {
        MaxValueMap<String> values = new MaxValueMap<String>();

        values.insert("A", 10);
        values.insert("B", 30);
        values.insert("C", 20);
        System.out.println(values.getMax()); // 30

        values.insert("B", 5);
        System.out.println(values.getMax()); // 20

        values.delete("C");
        System.out.println(values.getMax()); // 10
    }
}
