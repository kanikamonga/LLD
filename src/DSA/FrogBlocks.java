package DSA;

public class FrogBlocks {
	
	public int solution(int[] blocks) {
		int   n     = blocks.length;
		int[] left  = new int[n];
		int[] right = new int[n];
		
		// compute left reach
		left[0] = 0;
		for (int i = 1; i < n; i++) {
			if (blocks[i - 1] >= blocks[i]) {
				left[i] = left[i - 1];
			} else {
				left[i] = i;
			}
		}
		
		// compute right reach
		right[n - 1] = n - 1;
		for (int i = n - 2; i >= 0; i--) {
			if (blocks[i + 1] >= blocks[i]) {
				right[i] = right[i + 1];
			} else {
				right[i] = i;
			}
		}
		
		// compute maximum distance
		int maxDist = 1;
		int maxI    = -1;
		for (int i = 0; i < n; i++) {
			if (maxDist < right[i] - left[i] + 1) {
				maxDist = right[i] - left[i] + 1;
				maxI    = i;
			}
		}
		
		return maxDist;
	}
	
	public static void main(String[] args) {
		FrogBlocks fb     = new FrogBlocks();
		int[]      blocks = {1,1};
//		int[]      blocks = {1, 5, 5, 2, 6};
		System.out.println(fb.solution(blocks)); // Expected output: 3
	}
}
