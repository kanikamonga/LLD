package DSA.Amazon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/*
 * Problem:
 * Trim a binary tree so that it becomes a complete binary tree. Every removed
 * node must be added to a trash queue in level-order encounter order.
 *
 * A complete binary tree has no real node after the first missing child slot
 * in level order. Therefore, while scanning child slots from left to right:
 *
 *   - Before the first missing slot, keep every node.
 *   - After the first missing slot, remove every encountered node.
 *
 * Approach:
 * The queue stores a node together with its parent and whether it is the
 * parent's left or right child. A null slot creates the first gap. Any later
 * non-null node is detached from its parent and placed in the trash queue.
 * Its children are still visited so every removed node is reported.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 */
public class TrimToCompleteBinaryTree {

    public static class TreeNode {
        public int value;
        public TreeNode left;
        public TreeNode right;

        public TreeNode(int value) {
            this.value = value;
        }
    }

    public static class Result {
        public final TreeNode root;
        public final List<Integer> trashQueue;

        private Result(TreeNode root, List<Integer> trashQueue) {
            this.root = root;
            this.trashQueue = trashQueue;
        }
    }

    private static class Slot {
        private final TreeNode node;
        private final TreeNode parent;
        private final boolean isLeftChild;

        private Slot(TreeNode node, TreeNode parent, boolean isLeftChild) {
            this.node = node;
            this.parent = parent;
            this.isLeftChild = isLeftChild;
        }
    }

    public static Result trim(TreeNode root) {
        List<Integer> trashQueue = new ArrayList<Integer>();
        if (root == null) {
            return new Result(null, trashQueue);
        }

        Deque<Slot> queue = new ArrayDeque<Slot>();
        queue.offer(new Slot(root, null, false));
        boolean gapFound = false;

        while (!queue.isEmpty()) {
            Slot slot = queue.poll();

            if (slot.node == null) {
                // Any later real node would be to the right of a gap.
                gapFound = true;
                continue;
            }

            if (gapFound) {
                trashQueue.add(slot.node.value);

                // The descendants are also removed, but must be encountered
                // and added to the trash queue in level-order.
                queue.offer(new Slot(slot.node.left, slot.node, true));
                queue.offer(new Slot(slot.node.right, slot.node, false));
                detach(slot);
                continue;
            }

            // Keep this node and inspect both child slots in left-to-right
            // order. A missing left child is therefore seen before a right
            // child.
            queue.offer(new Slot(slot.node.left, slot.node, true));
            queue.offer(new Slot(slot.node.right, slot.node, false));
        }

        return new Result(root, trashQueue);
    }

    private static void detach(Slot slot) {
        if (slot.parent == null) {
            return;
        }
        if (slot.isLeftChild) {
            slot.parent.left = null;
        } else {
            slot.parent.right = null;
        }
    }

    public static void main(String[] args) {
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.left = new TreeNode(4);
        root.left.right = new TreeNode(5);
        root.right.left = new TreeNode(6);
        root.right.right = new TreeNode(7);

        Result result = trim(root);
        System.out.println("Root: " + result.root.value);
        System.out.println("Trash Queue: " + result.trashQueue);
    }
}
