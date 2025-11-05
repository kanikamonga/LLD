package DSA;

/**
 * You are given a binary string S of length N, which represents a non-negative integer V in big-endian format (the leftmost bit is the most
 * significant). Your task is to determine the minimum number of operations required to reduce V to 0. You can perform the following two
 * operations: If V is even → Divide V by 2. If V is odd → Subtract 1 from V. Example 1: Input: S = "011100" Output: 7 Explanation: S represents V
 * = 28 (binary 011100 → decimal 28). The value of V reduces as follows: 1. 28 → divide by 2 → 14 2. 14 → divide by 2 → 7 3. 7 → subtract 1 → 6 4.
 * 6 → divide by 2 → 3 5. 3 → subtract 1 → 2 6. 2 → divide by 2 → 1 7. 1 → subtract 1 → 0 Total **7 operations** required.
 */

public class MinOperationsBinary {
	
	public static int minOperations(String S) {
		// Remove leading zeros
		S = S.replaceFirst("^0+", "");
		if (S.isEmpty()) {
			return 0; // if input was all zeros
		}
		
		int ones = 0;
		for (char c : S.toCharArray()) {
			if (c == '1') {
				ones++;
			}
		}
		
		int length = S.length();
		
		return ones + length - 1;
	}
	
	public static void main(String[] args) {
		String S = "011100";
		System.out.println(minOperations(S)); // Output: 7
	}
}
