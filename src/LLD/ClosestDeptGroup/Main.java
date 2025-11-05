package LLD.ClosestDeptGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.

/**
 * for Set of Employee size : m
 * and for ShortestPath of any Employee : n
 * O(m*n)
 *
 * If any employee not found
 * If employee doesnt belong to any group
 * If group doesnt have any parent
 * If root group is null
 *
 *
 */

public class Main {
    public static void main(String[] args) {

		Group org = new Group();
	    org.name = "Org";
	    
	    Group engg = new Group();
	    engg.name = "Engg";
	    engg.parent = org;
	    
	    Group hr = new Group();
		hr.name = "HR";
		hr.parent = org;
		Employee mona = new Employee();
	    mona.name = "Mona";
		mona.parent = hr;
	    Employee springs = new Employee();
	    springs.name = "Springs";
		springs.parent = hr;
		
		List<Employee> employees = new ArrayList<>();
		employees.add(mona);
	    employees.add(springs);
		hr.employees = employees;
	    
	    Group be = new Group();
	    be.name = "BE";
	    be.employees = employees;
	    be.parent = engg;
	    
	    Employee alice = new Employee();
	    alice.name   = "Alice";
		alice.parent = be;
	    Employee bob = new Employee();
	    bob.name = "Bob";
		bob.parent = be;
	 
		employees = new ArrayList<>();
	    employees.add(alice);
	    employees.add(bob);
		
	    Group fe = new Group();
	    fe.name = "FE";
	    fe.employees = employees;
	    fe.parent = engg;
		
	    Employee lisa = new Employee();
	    lisa.name   = "Lisa";
		lisa.parent = fe;
	    
	    Employee marley = new Employee();
	    marley.name = "Marley";
		marley.parent = fe;
	    
	    employees = new ArrayList<>();
	    employees.add(lisa);
	    employees.add(marley);

		
		
		List<Group> subGroups = new ArrayList<>();
		subGroups.add(be);
		subGroups.add(fe);
		engg.subGroups = subGroups;
		
		
		LCA lca = new LCA();
		Group group = lca.findLca(Set.of(alice,bob));
	    System.out.println(group.name);
	    
	     group = lca.findLca(Set.of(alice,marley,springs));
	    System.out.println(group.name);
	    
    }
}