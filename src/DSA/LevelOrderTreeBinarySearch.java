package DSA;

import java.util.Arrays;

/*
 * Problem:
 * Given a complete binary tree whose values are sorted in level-order, and
 * the number of valid nodes, find a target value using binary search directly
 * on the level-order representation.
 *
 * The tree is represented as:
 *   left child of index i  = 2 * i + 1
 *   right child of index i = 2 * i + 2
 *
 * Because the level-order values are sorted, the tree can be searched just
 * like a sorted array. The node count is used instead of the backing array
 * length because the array may have unused capacity.
 *
 * Example:
 *   levelOrder = [1, 2, 3, 4, 5, 6, 7]
 *   nodeCount  = 7
 *   target     = 5
 *   result     = index 4
 *
 * Time Complexity: O(log n)
 * Space Complexity: O(1)
 *
 * Note:
 * If "sorted tree" means a binary search tree (BST), level-order values are
 * not necessarily sorted. In that case, compare the target with the root and
 * follow left/right child pointers; nodeCount is not required.
 */
public class LevelOrderTreeBinarySearch {

    /**
     * Returns the level-order index of target, or -1 when target is absent.
     */
    public static int findIndex(int[] levelOrder, int nodeCount, int target) {
        if (levelOrder == null || nodeCount < 0 || nodeCount > levelOrder.length) {
            throw new IllegalArgumentException("Invalid tree representation");
        }

        int left = 0;
        int right = nodeCount - 1;

        while (left <= right) {
            int middle = left + (right - left) / 2;

            if (levelOrder[middle] == target) {
                return middle;
            }
            if (levelOrder[middle] < target) {
                left = middle + 1;
            } else {
                right = middle - 1;
            }
        }

        return -1;
    }

    /**
     * Returns the target value when found, or null when it is absent.
     */
    public static Integer findValue(int[] levelOrder, int nodeCount, int target) {
        int index = findIndex(levelOrder, nodeCount, target);
        return index == -1 ? null : levelOrder[index];
    }

    public static void main(String[] args) {
        int[] levelOrder = {1, 2, 3, 4, 5, 6, 7};
        int target = 5;

        int index = findIndex(levelOrder, levelOrder.length, target);
        System.out.println("Tree: " + Arrays.toString(levelOrder));
        System.out.println("Target " + target + " found at index: " + index);
    }
}
