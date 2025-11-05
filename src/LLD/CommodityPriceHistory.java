package LLD;

import java.util.*;

public class CommodityPriceHistory {
    private final List<TreeMap<Long, Integer>> checkpoints = new ArrayList<>();
    private TreeMap<Long, Integer> currentMap = new TreeMap<>();

    // Upsert and create checkpoint
    public void upsert(long timestamp, int price) {
        // Copy previous snapshot (shallow copy for persistence-like behavior)
        currentMap = new TreeMap<>(currentMap);
        currentMap.put(timestamp, price);
        checkpoints.add(currentMap);
    }

    // Query: max price up to timestamp t at checkpoint c
    public int getMaxAtCheckpoint(long timestamp, int checkpointIndex) {
        if (checkpointIndex < 0 || checkpointIndex >= checkpoints.size()) {
            throw new IllegalArgumentException("Invalid checkpoint index");
        }
        TreeMap<Long, Integer> snapshot = checkpoints.get(checkpointIndex);

        // Only consider timestamps ≤ t
        NavigableMap<Long, Integer> valid = snapshot.headMap(timestamp, true);

        if (valid.isEmpty()) return Integer.MIN_VALUE;

        // Max over values
        return Collections.max(valid.values());
    }

    public int getCheckpointCount() {
        return checkpoints.size();
    }
}
