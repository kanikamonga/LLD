package DSA.Amazon;

import java.util.ArrayList;
import java.util.List;

/*
 * Anti-clockwise boundary traversal:
 *
 * 1. Add the root.
 * 2. Add the left boundary of the left subtree.
 * 3. Add leaves of the left subtree.
 * 4. Add the right boundary of the left subtree from bottom to top.
 * 5. Add leaves of the right subtree.
 * 6. Add the right boundary of the right subtree from bottom to top.
 *
 * This ordering matches the example, where 35 is included as the right
 * boundary of the left subtree.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(h) for recursion and the output list.
 */
public class AntiClockwiseBoundaryTraversal {

    public static class TreeNode {
        public int value;
        public TreeNode left;
        public TreeNode right;

        public TreeNode(int value) {
            this.value = value;
        }
    }

    public static List<Integer> boundaryTraversal(TreeNode root) {
        List<Integer> boundary = new ArrayList<Integer>();
        if (root == null) {
            return boundary;
        }

        // The root is always part of the boundary. Handle a one-node tree
        // separately so that the root is not added again as a leaf.
        if (!isLeaf(root)) {
            boundary.add(root.value);
        } else {
            boundary.add(root.value);
            return boundary;
        }

        // Add the left side from top to bottom.
        addLeftBoundary(root.left, boundary);

        // Add bottom/leaf nodes from left to right.
        addLeaves(root.left, boundary);

        // The example includes the right edge of the left subtree in reverse.
        addRightBoundary(root.left, boundary, false);

        // Process the right subtree's leaves, then its outer right edge.
        addLeaves(root.right, boundary);
        addRightBoundary(root.right, boundary, true);

        return boundary;
    }

    private static void addLeftBoundary(
            TreeNode node, List<Integer> boundary) {
        while (node != null) {
            // Prefer the left child; if it does not exist, follow the right
            // child so the boundary remains continuous for skewed trees.
            if (!isLeaf(node)) {
                boundary.add(node.value);
            }
            node = node.left != null ? node.left : node.right;
        }
    }

    private static void addLeaves(
            TreeNode node, List<Integer> boundary) {
        if (node == null) {
            return;
        }
        if (isLeaf(node)) {
            // Leaves are added once, in left-to-right order.
            boundary.add(node.value);
            return;
        }

        addLeaves(node.left, boundary);
        addLeaves(node.right, boundary);
    }

    private static void addRightBoundary(
            TreeNode node, List<Integer> boundary, boolean includeRoot) {
        List<Integer> rightBoundary = new ArrayList<Integer>();
        boolean isFirst = true;

        while (node != null) {
            // Save the boundary top-down first. It will be appended in reverse
            // to produce bottom-to-top anti-clockwise ordering.
            if ((!isFirst || includeRoot) && !isLeaf(node)) {
                rightBoundary.add(node.value);
            }
            node = node.right != null ? node.right : node.left;
            isFirst = false;
        }

        // Exclude the subtree root when requested to avoid duplicating it.
        for (int i = rightBoundary.size() - 1; i >= 0; i--) {
            boundary.add(rightBoundary.get(i));
        }
    }

    private static boolean isLeaf(TreeNode node) {
        return node.left == null && node.right == null;
    }

    public static void main(String[] args) {
        TreeNode root = new TreeNode(100);
        root.left = new TreeNode(50);
        root.right = new TreeNode(150);
        root.left.left = new TreeNode(25);
        root.left.right = new TreeNode(35);
        root.left.left.right = new TreeNode(30);
        root.left.right.left = new TreeNode(70);
        root.left.right.right = new TreeNode(80);
        root.right.left = new TreeNode(130);
        root.right.right = new TreeNode(210);

        System.out.println(boundaryTraversal(root));
        // [100, 50, 25, 30, 70, 80, 35, 130, 210, 150]
    }
}
