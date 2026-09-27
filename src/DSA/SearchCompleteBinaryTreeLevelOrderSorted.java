package DSA;

/*
 * ========================== PROBLEM DESCRIPTION ==========================
 *
 * Search in a Complete Binary Tree Sorted by Level-Order
 *
 * Given a complete binary tree where nodes are filled in level-order and values
 * are sorted according to level-order traversal:
 *
 *   If node u appears before node v in level-order traversal,
 *   then val(u) <= val(v).
 *
 * You are given:
 *   - root of the complete binary tree
 *   - N, the total number of nodes
 *   - target value
 *
 * Return true if target exists in the tree, otherwise false.
 *
 * Constraints:
 *   - 1 <= N <= 10^9
 *   - The tree is a valid complete binary tree.
 *
 * ========================== SOLUTION APPROACH =============================
 *
 * A complete binary tree can be treated like a 1-indexed array:
 *
 *   index 1 -> root
 *   index 2 -> root.left
 *   index 3 -> root.right
 *   index 4 -> root.left.left
 *   index 5 -> root.left.right
 *
 * Since the level-order traversal is sorted, this implicit array is sorted.
 * Therefore, we can binary search over indexes [1, N].
 *
 * To read the value at index i without an array:
 *   - Write i in binary.
 *   - Ignore the leading 1 because it represents the root.
 *   - For each remaining bit:
 *       0 -> move left
 *       1 -> move right
 *
 * Example:
 *   index 6 = binary 110
 *   skip first 1 -> 10
 *   path: right, then left
 *
 * Complexity:
 *   Binary search performs O(log N) probes.
 *   Each probe walks O(log N) tree height.
 *
 *   Time:  O(log^2 N)
 *   Space: O(log N), for the binary path string used to keep the logic simple.
 *
 * This is better than O(N) and works even when N is up to 10^9.
 *
 * =========================================================================
 */
public class SearchCompleteBinaryTreeLevelOrderSorted {

    static class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;

        TreeNode(int val) {
            this.val = val;
        }
    }

    public static boolean exists(TreeNode root, long n, int target) {
        if (root == null || n <= 0) {
            return false;
        }

        long left = 1;
        long right = n;

        while (left <= right) {
            long mid = left + (right - left) / 2;
            int midValue = getValueAtIndex(root, mid);

            if (midValue == target) {
                return true;
            }

            if (midValue < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return false;
    }

    private static int getValueAtIndex(TreeNode root, long index) {
        TreeNode current = root;
        String path = Long.toBinaryString(index).substring(1);

        // The leading binary digit represents the root, so each remaining digit is a move.
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '0') {
                current = current.left;
            } else {
                current = current.right;
            }
        }

        return current.val;
    }

    public static void main(String[] args) {
        /*
         * Level-order values:
         *   [1, 3, 5, 7, 9, 11, 13]
         *
         * Tree:
         *           1
         *        /     \
         *       3       5
         *     /  \     / \
         *    7    9   11 13
         */
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(3);
        root.right = new TreeNode(5);
        root.left.left = new TreeNode(7);
        root.left.right = new TreeNode(9);
        root.right.left = new TreeNode(11);
        root.right.right = new TreeNode(13);

        System.out.println(exists(root, 7, 9));  // true
        System.out.println(exists(root, 7, 10)); // false
    }
}
