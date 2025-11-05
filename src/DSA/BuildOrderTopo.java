package DSA;

import java.util.*;

/**
 * Develop a function that calculates the build order for a given software package based on its dependencies. Each package may have dependencies
 * that need to be built before it can be built. The function should utilize a given API, getDependencies(Package packageName), which returns a set
 * of immediate dependencies for a specified package. The build order should list packages in the order in which they can be safely built, ensuring
 * that all dependencies of a package are built before the package itself. Example 1: Input: Package Name: A Dependencies: A → {B,C}, B → {E}, C →
 * {D,E,F}, D → {}, F → {}, G → {C} Output: [E, B, F, D, C, A] Explanation: The package A depends on B and C. B itself depends on E, and C depends
 * on D, E, and F. Thus, E must be built before B, and D, E, and F must be built before C. This results in the build order of E, B, F, D, C, A.
 */
public class BuildOrderTopo {
	
	// Stub for dependency API
	private static Map<String, Set<String>> dependencyGraph = new HashMap<>();
	
	public static Set<String> getDependencies(String packageName) {
		return dependencyGraph.getOrDefault(packageName, Collections.emptySet());
	}
	
	// Topological sort (Kahn's Algorithm)
	public static List<String> buildOrder(Collection<String> allPackages) {
		// Step 1: Compute in-degree
		Map<String, Integer>      indegree = new HashMap<>();
		Map<String, List<String>> adjList  = new HashMap<>();
		
		for (String pkg : allPackages) {
			indegree.put(pkg, 0); // initialize
			adjList.put(pkg, new ArrayList<>());
		}
		
		for (String pkg : allPackages) {
			for (String dep : getDependencies(pkg)) {
				// dep must be built before pkg
				adjList.get(dep).add(pkg);
				indegree.put(pkg, indegree.get(pkg) + 1);
			}
		}
		
		// Step 2: Queue with in-degree = 0
		Queue<String> queue = new LinkedList<>();
		for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
			if (entry.getValue() == 0) {
				queue.offer(entry.getKey());
			}
		}
		
		// Step 3: Process queue
		List<String> order = new ArrayList<>();
		while (!queue.isEmpty()) {
			String pkg = queue.poll();
			order.add(pkg);
			
			for (String neighbor : adjList.get(pkg)) {
				indegree.put(neighbor, indegree.get(neighbor) - 1);
				if (indegree.get(neighbor) == 0) {
					queue.offer(neighbor);
				}
			}
		}
		
		// Step 4: Cycle check
		if (order.size() != allPackages.size()) {
			throw new RuntimeException("Cycle detected in dependencies");
		}
		
		return order;
	}
	
	// Example usage
	public static void main(String[] args) {
		// Dependency graph from the example
		dependencyGraph.put("A", new HashSet<>(Arrays.asList("B", "C")));
		dependencyGraph.put("B", new HashSet<>(Arrays.asList("E")));
		dependencyGraph.put("C", new HashSet<>(Arrays.asList("D", "E", "F")));
		dependencyGraph.put("D", new HashSet<>());
		dependencyGraph.put("E", new HashSet<>());
		dependencyGraph.put("F", new HashSet<>());
		dependencyGraph.put("G", new HashSet<>(Arrays.asList("C")));
		
		Set<String> allPackages = dependencyGraph.keySet();
		
		List<String> order = buildOrder(allPackages);
		System.out.println(order); // Example: [E, B, F, D, C, A, G]
	}
}
