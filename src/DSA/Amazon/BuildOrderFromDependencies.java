package DSA.Amazon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class BuildOrderFromDependencies {

    // dependencies.get(package) returns its immediate dependencies.
    public static List<String> buildOrder(
            String packageName, Map<String, List<String>> dependencies) {

        Set<String> packages = collectPackages(packageName, dependencies);
        Map<String, List<String>> graph = new HashMap<String, List<String>>();
        Map<String, Integer> indegree = new HashMap<String, Integer>();

        for (String packageId : packages) {
            graph.put(packageId, new ArrayList<String>());
            indegree.put(packageId, 0);
        }

        // Reverse each edge: dependency -> package.
        for (String packageId : packages) {
            for (String dependency : dependencies.getOrDefault(
                    packageId, new ArrayList<String>())) {
                graph.get(dependency).add(packageId);
                indegree.put(packageId, indegree.get(packageId) + 1);
            }
        }

        Queue<String> ready = new ArrayDeque<String>();
        for (String packageId : packages) {
            if (indegree.get(packageId) == 0) {
                ready.offer(packageId);
            }
        }

        List<String> order = new ArrayList<String>();
        while (!ready.isEmpty()) {
            String current = ready.poll();
            order.add(current);

            for (String dependent : graph.get(current)) {
                indegree.put(dependent, indegree.get(dependent) - 1);
                if (indegree.get(dependent) == 0) {
                    ready.offer(dependent);
                }
            }
        }

        if (order.size() != packages.size()) {
            throw new IllegalArgumentException("Circular dependency detected");
        }
        return order;
    }

    private static Set<String> collectPackages(
            String root, Map<String, List<String>> dependencies) {
        Set<String> packages = new HashSet<String>();
        Queue<String> queue = new ArrayDeque<String>();
        queue.offer(root);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!packages.add(current)) {
                continue;
            }

            for (String dependency : dependencies.getOrDefault(
                    current, new ArrayList<String>())) {
                queue.offer(dependency);
            }
        }
        return packages;
    }

    public static void main(String[] args) {
        Map<String, List<String>> dependencies = new HashMap<String, List<String>>();
        dependencies.put("A", java.util.Arrays.asList("B", "C"));
        dependencies.put("B", java.util.Arrays.asList("E"));
        dependencies.put("C", java.util.Arrays.asList("D", "E", "F"));
        dependencies.put("D", new ArrayList<String>());
        dependencies.put("E", new ArrayList<String>());
        dependencies.put("F", new ArrayList<String>());
        dependencies.put("G", java.util.Arrays.asList("C"));

        System.out.println(buildOrder("A", dependencies));
    }
}
