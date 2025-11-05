package LLD.ClosestDeptGroup;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Full Java implementation of an organization group hierarchy with copy-on-write snapshots.
 *
 * - OrgSnapshot is immutable (shallow-immutable) and designed for cheap copy-on-write updates: writers
 *   create a new OrgSnapshot with only modified maps/sets copied; unchanged maps/sets are reused.
 * - OrgService exposes read method getCommonGroupForEmployees and a set of writer methods that
 *   atomically swap snapshots using CAS so readers always see a consistent state.
 * - The "closest" group is defined as the group that minimizes the maximum shortest-path
 *   distance (along parent edges) from each employee's groups to that group. Tie-breaking
 *   uses lexicographic groupId order.
 *
 * Note: This implementation favors clarity and correctness. For extremely large orgs or
 * very high update rates, consider using persistent collections (e.g., PCollections) or
 * an index for transitive closure.
 */
public class OrgClosestCommonGroup {


    /**
     * Main service exposing read and write operations. Readers use the immutable snapshot
     * referenced by snapshotRef and do not require locks. Writers create new snapshots and
     * swap them in with compare-and-set to guarantee atomic visibility.
     */


    // ========== Simple demo / quick tests ===========
    public static void main(String[] args) {
        OrgService svc = new OrgService(false); // false -> allow parents

        // create groups
        svc.addGroup("Engineering");
        svc.addGroup("Platform");
        svc.addGroup("Cloud");
        svc.addGroup("Mobile");

        // hierarchy: Mobile -> Engineering -> Platform
        svc.addParentRelation("Mobile", "Engineering");
        svc.addParentRelation("Engineering", "Platform");
        svc.addParentRelation("Cloud", "Platform");

        // memberships
        svc.addMembership("alice", "Mobile");
        svc.addMembership("bob", "Cloud");
        svc.addMembership("carol", "Engineering");

        // Query: closest common group for {alice, carol}
        Optional<String> c1 = svc.getCommonGroupForEmployees(new HashSet<>(Arrays.asList("alice", "carol")));
        System.out.println("Common group (alice, carol): " + c1.orElse("<none>")); // expect Engineering

        // Query: closest common group for {alice, bob}
        Optional<String> c2 = svc.getCommonGroupForEmployees(new HashSet<>(Arrays.asList("alice", "bob")));
        System.out.println("Common group (alice, bob): " + c2.orElse("<none>")); // expect Platform

        // single-level scenario
        OrgService single = new OrgService(true);
        single.addGroup("G1"); single.addGroup("G2");
        single.addMembership("u1", "G1"); single.addMembership("u2", "G1");
        single.addMembership("u2", "G2");
        System.out.println("Single-level common (u1,u2): " + single.getCommonGroupForEmployees(new HashSet<>(Arrays.asList("u1","u2"))).orElse("<none>"));
        // expect G1
    }
}
