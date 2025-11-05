package DSA;

import java.util.*;

/**
 * On a 2D plane you given N right angled triangles, which can translate in space but not rotate.
 * Return pairs of triangles which for rectangle. He
 * didn't give me any input, instead asked me, how will I store these triangles and return the pairs.
 */
class RightTriangle {
	int x, y;   // right-angle vertex
	int a, b;   // leg lengths (a along X-axis, b along Y-axis)
	
	RightTriangle(int x, int y, int a, int b) {
		this.x = x;
		this.y = y;
		this.a = a;
		this.b = b;
	}
	
	// Key for hashmap (legs only)
	String getKey() {
		return a + "," + b;
	}
	
	@Override
	public String toString() {
		return "Triangle((" + x + "," + y + "), a=" + a + ", b=" + b + ")";
	}
}

class TrianglePair {
	RightTriangle t1, t2;
	
	TrianglePair(RightTriangle t1, RightTriangle t2) {
		this.t1 = t1;
		this.t2 = t2;
	}
	
	@Override
	public String toString() {
		return "[" + t1 + " , " + t2 + "]";
	}
}

public class RectanglesFromTriangles {
	
	// Check if two triangles form a rectangle
	private static boolean formsRectangle(RightTriangle t1, RightTriangle t2) {
		// They must have same legs
		if (t1.a != t2.a || t1.b != t2.b) {
			return false;
		}
		
		// Possible partner positions:
		// One at (x,y), other at (x+a,y)  OR
		// One at (x,y), other at (x,y+b)
		return (t2.x == t1.x + t1.a && t2.y == t1.y) || (t2.x == t1.x && t2.y == t1.y + t1.b);
	}
	
	public static List<TrianglePair> findRectanglePairs(List<RightTriangle> triangles) {
		Map<String, List<RightTriangle>> map    = new HashMap<>();
		List<TrianglePair>               result = new ArrayList<>();
		
		for (RightTriangle t : triangles) {
			String key = t.getKey();
			
			if (map.containsKey(key)) {
				for (RightTriangle other : map.get(key)) {
					if (formsRectangle(t, other)) {
						result.add(new TrianglePair(t, other));
					}
				}
			}
			
			map.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
		}
		
		return result;
	}
	
	// Example usage
	public static void main(String[] args) {
		List<RightTriangle> triangles = new ArrayList<>();
		triangles.add(new RightTriangle(0, 0, 2, 3));
		triangles.add(new RightTriangle(2, 0, 2, 3)); // forms rectangle with (0,0)
		triangles.add(new RightTriangle(5, 5, 2, 3));
		triangles.add(new RightTriangle(5, 8, 2, 3)); // forms rectangle with (5,5)
		triangles.add(new RightTriangle(10, 10, 1, 1)); // no partner
		
		List<TrianglePair> result = findRectanglePairs(triangles);
		
		for (TrianglePair pair : result) {
			System.out.println(pair);
		}
	}
}
