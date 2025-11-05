package DSA.closestGroup;

import java.util.*;

/**
 * Immutable Org Group class
 */
class Group {
    private final String id;
    private final List<Group> children;
    private final List<String> employees;
    private final Group parent; // parent is optional

    private Group(String id, Group parent, List<Group> children, List<String> employees) {
        this.id = id;
        this.parent = parent;
        this.children = Collections.unmodifiableList(children);
        this.employees = Collections.unmodifiableList(employees);
    }

    public String getId() {
        return id;
    }

    public Group getParent() {
        return parent;
    }

    public List<Group> getChildren() {
        return children;
    }

    public List<String> getEmployees() {
        return employees;
    }

    // Builder pattern to safely construct groups
    public static class Builder {
        private final String id;
        private Group parent;
        private final List<Group> children = new ArrayList<>();
        private final List<String> employees = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
        }

        public Builder parent(Group parent) {
            this.parent = parent;
            return this;
        }

        public Builder addChild(Group child) {
            children.add(child);
            return this;
        }

        public Builder addEmployee(String empId) {
            employees.add(empId);
            return this;
        }

        public Group build() {
            return new Group(id, parent, children, employees);
        }
    }
}

/**
 * Org Directory maintains mapping from employees -> groups
 */
class OrgDirectory {
    private final Group root;
    private final Map<String, Group> employeeToGroup;

    public OrgDirectory(Group root, Map<String, Group> employeeToGroup) {
        this.root = root;
        this.employeeToGroup = employeeToGroup;
    }

    /**
     * Find the closest common parent group for a set of employees
     */
    public Group findClosestCommonGroup(List<String> employees) {
	    if (employees.isEmpty()) {
		    return null;
	    }

        List<List<Group>> paths = new ArrayList<>();

        // Step 1: collect ancestor chains
        for (String emp : employees) {
            Group g = employeeToGroup.get(emp);
	        if (g == null) {
		        throw new IllegalArgumentException("Unknown employee: " + emp);
	        }

            List<Group> path = new ArrayList<>();
            while (g != null) {
                path.add(g);
                g = g.getParent();
            }
            Collections.reverse(path);
            paths.add(path);
        }

        // Step 2: Find longest common prefix
        Group result = null;
        for (int i = 0; ; i++) {
            Group candidate = paths.get(0).get(i);
            for (List<Group> p : paths) {
                if (i >= p.size() || p.get(i) != candidate) {
                    return result;
                }
            }
            result = candidate;
        }
    }
}

/**
 * Example usage
 */
public class ClosestCommonOrg {
    public static void main(String[] args) {
        // Build Org Hierarchy
        Group root = new Group.Builder("OrgRoot").build();
        Group deptX = new Group.Builder("DeptX").parent(root).build();
        Group deptY = new Group.Builder("DeptY").parent(root).build();
        Group teamA = new Group.Builder("TeamA").parent(deptX).build();
        Group teamB = new Group.Builder("TeamB").parent(deptX).build();
        Group teamC = new Group.Builder("TeamC").parent(deptY).build();

        // Assign employees
        Map<String, Group> empMap = new HashMap<>();
        empMap.put("E1", teamA);
        empMap.put("E2", teamB);
        empMap.put("E3", teamC);

        OrgDirectory directory = new OrgDirectory(root, empMap);

        // Queries
        System.out.println("Closest Org for [E1, E2]: " +
                directory.findClosestCommonGroup(Arrays.asList("E1", "E2")).getId()); // DeptX

        System.out.println("Closest Org for [E1, E3]: " +
                directory.findClosestCommonGroup(Arrays.asList("E1", "E3")).getId()); // OrgRoot
    }
}
