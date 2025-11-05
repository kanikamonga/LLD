package LLD.ClosestDeptGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LCA {
	
	public Group findLca(Set<Employee> employeeSet) {
		
		List<List<Group>> paths = new ArrayList<>();
		for (Employee e : employeeSet) {
			Group parent = e.parent;
			if (parent == null) {
				return null;
			}
			
			List<Group> parentPath = new ArrayList<>();
			while (parent != null) {
				parentPath.add(parent);
				parent = parent.parent;
			}
			Collections.reverse(parentPath);
			paths.add(parentPath);
		}
		
		Group lca = null;
		
		for (List<Group> p : paths) {
			System.out.println("Path : ");
			p.stream().forEach(g -> System.out.print(g.name));
			System.out.println();
		}
		
		for (int i = 0; i < paths.get(0).size() ; i++) {
			Group p = paths.get(0).get(i);
			for (List<Group> path : paths) {
				if (i >= path.size() || path.get(i)!=p) {
					return lca;
				}
			}
			lca = p;
		}
		
		return lca;
	}
	
}
