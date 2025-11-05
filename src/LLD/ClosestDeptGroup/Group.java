package LLD.ClosestDeptGroup;

import java.util.List;

public class Group {
	String         name;
	List<Group>    subGroups;
	List<Employee> employees;
	Group parent;
}
