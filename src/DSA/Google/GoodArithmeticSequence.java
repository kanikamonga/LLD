package DSA.Google;

/**
 * An arithmetic sequence is a list of numbers where the difference between any two consecutive elements is constant. A good arithmetic sequence is specifically defined as a sequence where the common difference is either $1$ or $-1$. By definition, any sequence containing only a single element is also a good arithmetic sequence.
 * <p>
 * Given an integer array nums, calculate and return the sum of the sums of all subarrays that qualify as good arithmetic sequences.
 * <p>
 * Input Format:
 * An integer array nums.
 * <p>
 * Output Format:
 * A long integer representing the total sum of all sums of qualifying subarrays.
 */
public class GoodArithmeticSequence {

    public static long sumOfGoodArithmeticSubarrays(int[] nums) {
        int n = nums.length;
        long totalSum = 0;

        // All single-element subarrays are good
        for (int x : nums) totalSum += x;

        // Process maximal runs with common difference +1 and -1
        for (int d : new int[]{1, -1}) {
            int i = 0;
            while (i < n) {
                int j = i;
                while (j + 1 < n && nums[j + 1] - nums[j] == d) {
                    j++;
                }
                int L = j - i + 1;
                if (L >= 2) {
                    // Each element at position k (0-indexed within run) appears in
                    // (k+1)*(L-k) total subarrays, minus 1 for the single-element one
                    for (int k = 0; k < L; k++) {
                        long count = (long) (k + 1) * (L - k) - 1;
                        totalSum += (long) nums[i + k] * count;
                    }
                }
                i = j + 1;
            }
        }

        return totalSum;
    }

    public static void main(String[] args) {
        System.out.println(sumOfGoodArithmeticSubarrays(new int[]{7, 4, 5, 6, 5})); // 73
        System.out.println(sumOfGoodArithmeticSubarrays(new int[]{1, 2, 3}));       // 20
    }
}
