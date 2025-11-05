package LLD.ClosestDeptGroup;

import java.util.*;

/** Immutable snapshot of the org state. */
    public class OrgSnapshot {
        // employeeId -> set of groupIds the employee directly belongs to
        private final Map<String, Set<String>> employeeGroups;
        // groupId -> set of parent groupIds (can be empty if no parents)
        private final Map<String, Set<String>> groupParents;
        // groupId -> set of child groupIds (maintained for convenience)
        private final Map<String, Set<String>> groupChildren;
        // set of all groups
        private final Set<String> groups;

        private OrgSnapshot(Map<String, Set<String>> employeeGroups,
                            Map<String, Set<String>> groupParents,
                            Map<String, Set<String>> groupChildren,
                            Set<String> groups) {
            this.employeeGroups = Collections.unmodifiableMap(employeeGroups);
            this.groupParents = Collections.unmodifiableMap(groupParents);
            this.groupChildren = Collections.unmodifiableMap(groupChildren);
            this.groups = Collections.unmodifiableSet(groups);
        }

        // factory for empty snapshot
        public static OrgSnapshot empty() {
            return new OrgSnapshot(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashSet<>());
        }

        // getters (note: returns unmodifiable collections)
        public Map<String, Set<String>> getEmployeeGroups() { return employeeGroups; }
        public Map<String, Set<String>> getGroupParents() { return groupParents; }
        public Map<String, Set<String>> getGroupChildren() { return groupChildren; }
        public Set<String> getGroups() { return groups; }

        // helper methods used by writers to create modified snapshots using copy-on-write

        private static <K,V> Map<K,V> shallowCopy(Map<K,V> m) {
            return new HashMap<>(m);
        }

        private static <T> Set<T> copyAndUnmod(Set<T> s) {
            return (s == null) ? new HashSet<>() : new HashSet<>(s);
        }

        private static <K,V> Map<K,V> copyAndPut(Map<K,V> original, K key, V value) {
            Map<K,V> n = shallowCopy(original);
            if (value == null) n.remove(key);
            else n.put(key, value);
            return n;
        }

        /**
         * Returns a new snapshot with the given group added (no parents by default).
         */
        public OrgSnapshot withAddedGroup(String groupId) {
            if (groups.contains(groupId)) return this; // no-op
            Map<String, Set<String>> eg = shallowCopy(employeeGroups);
            Map<String, Set<String>> gp = shallowCopy(groupParents);
            Map<String, Set<String>> gc = shallowCopy(groupChildren);
            Set<String> gset = new HashSet<>(groups);
            gset.add(groupId);
            // initialize empty parent/child sets
            gp.put(groupId, Collections.unmodifiableSet(new HashSet<>()));
            gc.put(groupId, Collections.unmodifiableSet(new HashSet<>()));
            return new OrgSnapshot(eg, gp, gc, gset);
        }

        /**
         * Returns a new snapshot with the given group removed. Also removes relations and memberships.
         */
        public OrgSnapshot withRemovedGroup(String groupId) {
            if (!groups.contains(groupId)) return this; // no-op
            Map<String, Set<String>> eg = shallowCopy(employeeGroups);
            Map<String, Set<String>> gp = shallowCopy(groupParents);
            Map<String, Set<String>> gc = shallowCopy(groupChildren);
            Set<String> gset = new HashSet<>(groups);
            gset.remove(groupId);

            // remove group from gp/gc maps
            gp.remove(groupId);
            gc.remove(groupId);

            // remove from other groups' parent/child sets
            for (Map.Entry<String, Set<String>> e : gp.entrySet()) {
                if (e.getValue().contains(groupId)) {
                    Set<String> newParents = new HashSet<>(e.getValue());
                    newParents.remove(groupId);
                    gp.put(e.getKey(), Collections.unmodifiableSet(newParents));
                }
            }
            for (Map.Entry<String, Set<String>> e : gc.entrySet()) {
                if (e.getValue().contains(groupId)) {
                    Set<String> newChildren = new HashSet<>(e.getValue());
                    newChildren.remove(groupId);
                    gc.put(e.getKey(), Collections.unmodifiableSet(newChildren));
                }
            }

            // remove memberships of this group
            for (Map.Entry<String, Set<String>> e : eg.entrySet()) {
                if (e.getValue().contains(groupId)) {
                    Set<String> newGroups = new HashSet<>(e.getValue());
                    newGroups.remove(groupId);
                    if (newGroups.isEmpty()) eg.put(e.getKey(), Collections.unmodifiableSet(new HashSet<>()));
                    else eg.put(e.getKey(), Collections.unmodifiableSet(newGroups));
                }
            }

            return new OrgSnapshot(eg, gp, gc, gset);
        }

        /**
         * Add parent relationship: child -> parent. (i.e., parent becomes a parent of child)
         */
        public OrgSnapshot withAddedParentRelation(String childGroup, String parentGroup) {
            if (!groups.contains(childGroup) || !groups.contains(parentGroup)) {
                throw new IllegalArgumentException("groups must exist before creating relation");
            }
            // copy maps shallowly and replace affected sets
            Map<String, Set<String>> gp = shallowCopy(groupParents);
            Map<String, Set<String>> gc = shallowCopy(groupChildren);

            // parents of child
            Set<String> oldParents = gp.getOrDefault(childGroup, Collections.emptySet());
            if (oldParents.contains(parentGroup)) return this; // already present
            Set<String> newParents = new HashSet<>(oldParents);
            newParents.add(parentGroup);
            gp.put(childGroup, Collections.unmodifiableSet(newParents));

            // children of parent
            Set<String> oldChildren = gc.getOrDefault(parentGroup, Collections.emptySet());
            Set<String> newChildren = new HashSet<>(oldChildren);
            newChildren.add(childGroup);
            gc.put(parentGroup, Collections.unmodifiableSet(newChildren));

            return new OrgSnapshot(employeeGroups, gp, gc, groups);
        }

        /**
         * Remove parent relation child -> parent.
         */
        public OrgSnapshot withRemovedParentRelation(String childGroup, String parentGroup) {
            if (!groups.contains(childGroup) || !groups.contains(parentGroup)) {
                throw new IllegalArgumentException("groups must exist");
            }
            Map<String, Set<String>> gp = shallowCopy(groupParents);
            Map<String, Set<String>> gc = shallowCopy(groupChildren);

            Set<String> oldParents = gp.getOrDefault(childGroup, Collections.emptySet());
            if (!oldParents.contains(parentGroup)) return this; // no-op
            Set<String> newParents = new HashSet<>(oldParents);
            newParents.remove(parentGroup);
            gp.put(childGroup, Collections.unmodifiableSet(newParents));

            Set<String> oldChildren = gc.getOrDefault(parentGroup, Collections.emptySet());
            Set<String> newChildren = new HashSet<>(oldChildren);
            newChildren.remove(childGroup);
            gc.put(parentGroup, Collections.unmodifiableSet(newChildren));

            return new OrgSnapshot(employeeGroups, gp, gc, groups);
        }

        /**
         * Add a membership: employee -> group
         */
        public OrgSnapshot withAddedMembership(String employeeId, String groupId) {
            if (!groups.contains(groupId)) {
                throw new IllegalArgumentException("group must exist before adding membership");
            }
            Map<String, Set<String>> eg = shallowCopy(employeeGroups);
            Set<String> existing = eg.getOrDefault(employeeId, Collections.emptySet());
            if (existing.contains(groupId)) return this; // no-op
            Set<String> newSet = new HashSet<>(existing);
            newSet.add(groupId);
            eg.put(employeeId, Collections.unmodifiableSet(newSet));
            return new OrgSnapshot(eg, groupParents, groupChildren, groups);
        }

        /**
         * Remove a membership employee -> group
         */
        public OrgSnapshot withRemovedMembership(String employeeId, String groupId) {
            if (!groups.contains(groupId)) return this; // nothing to do
            if (!employeeGroups.containsKey(employeeId)) return this;
            Map<String, Set<String>> eg = shallowCopy(employeeGroups);
            Set<String> existing = eg.getOrDefault(employeeId, Collections.emptySet());
            if (!existing.contains(groupId)) return this; // no-op
            Set<String> newSet = new HashSet<>(existing);
            newSet.remove(groupId);
            eg.put(employeeId, Collections.unmodifiableSet(newSet));
            return new OrgSnapshot(eg, groupParents, groupChildren, groups);
        }
    }
