package LLD.ClosestDeptGroup;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class OrgService {
    private final AtomicReference<OrgSnapshot> snapshotRef;
    private final boolean singleLevelMode; // if true, no groupParents relations matter

    public OrgService(boolean singleLevelMode) {
        this.snapshotRef = new AtomicReference<>(OrgSnapshot.empty());
        this.singleLevelMode = singleLevelMode;
    }

    // ========== Reader API ===========

    /**
     * Returns an Optional containing the closest common group for the given employee IDs
     * using the "minimize max distance" metric. Tie-breaker: lexicographically smallest groupId.
     */
    public Optional<String> getCommonGroupForEmployees(Set<String> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) return Optional.empty();
        OrgSnapshot snap = snapshotRef.get();

        if (singleLevelMode) return getCommonGroupSingleLevel(snap, employeeIds);

        // General DAG case: compute distances per employee using BFS upwards.
        // For each employee e compute map group -> distance (shortest)
        Map<String, Map<String, Integer>> distPerEmployee = new HashMap<>();
        for (String emp : employeeIds) {
            Set<String> startGroups = snap.getEmployeeGroups().getOrDefault(emp, Collections.emptySet());
            if (startGroups.isEmpty()) return Optional.empty(); // employee not in any group
            Map<String, Integer> dist = bfsUpwardsDistances(snap.getGroupParents(), startGroups);
            if (dist.isEmpty()) return Optional.empty();
            distPerEmployee.put(emp, dist);
        }

        // Intersect reachable groups across all employees by scanning groups present in snapshot
        String bestGroup = null;
        int bestMaxDist = Integer.MAX_VALUE;
        for (String g : snap.getGroups()) {
            int maxd = 0;
            boolean reachableByAll = true;
            for (String emp : employeeIds) {
                Integer d = distPerEmployee.get(emp).get(g);
                if (d == null) {
                    reachableByAll = false;
                    break;
                }
                maxd = Math.max(maxd, d);
            }
            if (!reachableByAll) continue;
            if (bestGroup == null || maxd < bestMaxDist || (maxd == bestMaxDist && g.compareTo(bestGroup) < 0)) {
                bestGroup = g;
                bestMaxDist = maxd;
            }
        }
        return Optional.ofNullable(bestGroup);
    }

    // optimized for single-level orgs (no parent relations)
    private Optional<String> getCommonGroupSingleLevel(OrgSnapshot snap, Set<String> employeeIds) {
        // collect group frequency
        Map<String, Integer> freq = new HashMap<>();
        for (String emp : employeeIds) {
            Set<String> groups = snap.getEmployeeGroups().getOrDefault(emp, Collections.emptySet());
            if (groups.isEmpty()) return Optional.empty();
            for (String g : groups) freq.put(g, freq.getOrDefault(g, 0) + 1);
        }
        int needed = employeeIds.size();
        String best = null;
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() == needed) {
                if (best == null || e.getKey().compareTo(best) < 0) best = e.getKey();
            }
        }
        return Optional.ofNullable(best);
    }

    // BFS to compute shortest distances from any startGroup upwards following parent links
    private Map<String, Integer> bfsUpwardsDistances(Map<String, Set<String>> groupParents, Set<String> startGroups) {
        Map<String, Integer> dist = new HashMap<>();
        ArrayDeque<String> q = new ArrayDeque<>();
        for (String g : startGroups) {
            dist.put(g, 0);
            q.add(g);
        }
        while (!q.isEmpty()) {
            String cur = q.remove();
            int cd = dist.get(cur);
            Set<String> parents = groupParents.getOrDefault(cur, Collections.emptySet());
            for (String p : parents) {
                if (!dist.containsKey(p)) {
                    dist.put(p, cd + 1);
                    q.add(p);
                }
            }
        }
        return dist;
    }

    // ========== Writer API (copy-on-write) ===========

    public void addGroup(String groupId) {
        while (true) {
            OrgSnapshot cur = snapshotRef.get();
            OrgSnapshot next = cur.withAddedGroup(groupId);
            if (snapshotRef.compareAndSet(cur, next)) return;
        }
    }

    public void removeGroup(String groupId) {
        while (true) {
            OrgSnapshot cur = snapshotRef.get();
            OrgSnapshot next = cur.withRemovedGroup(groupId);
            if (snapshotRef.compareAndSet(cur, next)) return;
        }
    }

    public void addParentRelation(String childGroup, String parentGroup) {
        while (true) {
            OrgSnapshot cur = snapshotRef.get();
            OrgSnapshot next = cur.withAddedParentRelation(childGroup, parentGroup);
            if (snapshotRef.compareAndSet(cur, next)) return;
        }
    }

    public void removeParentRelation(String childGroup, String parentGroup) {
        while (true) {
            OrgSnapshot cur = snapshotRef.get();
            OrgSnapshot next = cur.withRemovedParentRelation(childGroup, parentGroup);
            if (snapshotRef.compareAndSet(cur, next)) return;
        }
    }

    public void addMembership(String employeeId, String groupId) {
        while (true) {
            OrgSnapshot cur = snapshotRef.get();
            OrgSnapshot next = cur.withAddedMembership(employeeId, groupId);
            if (snapshotRef.compareAndSet(cur, next)) return;
        }
    }

    public void removeMembership(String employeeId, String groupId) {
        while (true) {
            OrgSnapshot cur = snapshotRef.get();
            OrgSnapshot next = cur.withRemovedMembership(employeeId, groupId);
            if (snapshotRef.compareAndSet(cur, next)) return;
        }
    }

    // convenience: bulk replace snapshot (e.g., for batch updates)
    public void replaceSnapshot(OrgSnapshot newSnap) {
        snapshotRef.set(newSnap);
    }

    public OrgSnapshot currentSnapshot() {
        return snapshotRef.get();
    }
}