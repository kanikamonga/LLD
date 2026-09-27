package DSA.Atlassian;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * ========================== PROBLEM DESCRIPTION ==========================
 *
 * 2386. Lowest Common Ancestor (LCA) in an N-ary Tree for Leaf Nodes
 *
 * Given the root of an N-ary tree, find the Lowest Common Ancestor (LCA) for
 * any two distinct leaf nodes in the tree.
 *
 * Input:
 *   The root node of an N-ary tree and two distinct leaf nodes, node1 and node2.
 *
 *   class NaryTreeNode {
 *       int val;
 *       List<NaryTreeNode> children;
 *   }
 *
 * Output:
 *   The NaryTreeNode that represents the LCA of node1 and node2.
 *
 * Constraints:
 *   - Number of nodes is between 1 and 10^5.
 *   - Node values are unique.
 *   - node1 and node2 are guaranteed to be distinct leaf nodes in the tree.
 *   - Time complexity should be optimal, ideally O(N).
 *
 * ========================== SOLUTION APPROACH =============================
 *
 * Use an iterative DFS to build parent pointers from child -> parent.
 * Then:
 *   1. Add all ancestors of node1 to a set.
 *   2. Walk upward from node2 using the parent map.
 *   3. The first ancestor also present in node1's ancestor set is the LCA.
 *
 * This avoids recursive DFS stack overflow for a skewed tree with 10^5 nodes.
 *
 * Complexity:
 *   Time:  O(N), each node is visited at most once.
 *   Space: O(N), for the parent map and ancestor set.
 *
 * =========================================================================
 */
public class LowestCommonAncestorNaryTreeLeafNodes {

    static class NaryTreeNode {
        int val;
        List<NaryTreeNode> children;

        NaryTreeNode(int val) {
            this.val = val;
            this.children = new ArrayList<NaryTreeNode>();
        }
    }

    public static NaryTreeNode lowestCommonAncestor(
            NaryTreeNode root,
            NaryTreeNode node1,
            NaryTreeNode node2) {
        if (root == null || node1 == null || node2 == null || node1 == node2) {
            throw new IllegalArgumentException("Input nodes must be non-null and distinct");
        }

        Map<NaryTreeNode, NaryTreeNode> parent = new IdentityHashMap<NaryTreeNode, NaryTreeNode>();
        buildParentMap(root, node1, node2, parent);

        return findLcaUsingParentMap(parent, node1, node2);
    }

    public static NaryTreeNode lowestCommonAncestorBfs(
            NaryTreeNode root,
            NaryTreeNode node1,
            NaryTreeNode node2) {
        if (root == null || node1 == null || node2 == null || node1 == node2) {
            throw new IllegalArgumentException("Input nodes must be non-null and distinct");
        }

        Map<NaryTreeNode, NaryTreeNode> parent = new IdentityHashMap<NaryTreeNode, NaryTreeNode>();
        buildParentMapBfs(root, node1, node2, parent);

        return findLcaUsingParentMap(parent, node1, node2);
    }

    private static NaryTreeNode findLcaUsingParentMap(
            Map<NaryTreeNode, NaryTreeNode> parent,
            NaryTreeNode node1,
            NaryTreeNode node2) {
        // Defensive validation: the problem guarantees this, but it keeps the API safe.
        if (!parent.containsKey(node1) || !parent.containsKey(node2)) {
            throw new IllegalArgumentException("Both nodes must exist in the tree");
        }

        // Store node1's complete ancestor chain, including node1 and root.
        Set<NaryTreeNode> node1Ancestors = new HashSet<NaryTreeNode>();
        NaryTreeNode current = node1;
        while (current != null) {
            node1Ancestors.add(current);
            current = parent.get(current);
        }

        // The first ancestor of node2 found in node1's chain is the lowest common ancestor.
        current = node2;
        while (current != null) {
            if (node1Ancestors.contains(current)) {
                return current;
            }
            current = parent.get(current);
        }

        throw new IllegalStateException("The provided tree is disconnected");
    }

    private static void buildParentMap(
            NaryTreeNode root,
            NaryTreeNode node1,
            NaryTreeNode node2,
            Map<NaryTreeNode, NaryTreeNode> parent) {
        ArrayDeque<NaryTreeNode> stack = new ArrayDeque<NaryTreeNode>();
        stack.push(root);

        // Root has no parent; null marks the top of the tree.
        parent.put(root, null);

        boolean foundNode1 = root == node1;
        boolean foundNode2 = root == node2;

        // Iterative DFS avoids recursion depth issues for a skewed tree of 10^5 nodes.
        while (!stack.isEmpty() && (!foundNode1 || !foundNode2)) {
            NaryTreeNode current = stack.pop();

            for (NaryTreeNode child : current.children) {
                // Record the upward edge so we can later walk from a leaf to the root.
                parent.put(child, current);
                if (child == node1) {
                    foundNode1 = true;
                }
                if (child == node2) {
                    foundNode2 = true;
                }

                // Children are still pushed so their descendants can be processed if needed.
                stack.push(child);
            }
        }
    }

    private static void buildParentMapBfs(
            NaryTreeNode root,
            NaryTreeNode node1,
            NaryTreeNode node2,
            Map<NaryTreeNode, NaryTreeNode> parent) {
        ArrayDeque<NaryTreeNode> queue = new ArrayDeque<NaryTreeNode>();
        queue.offer(root);

        // Root has no parent; null marks the top of the tree.
        parent.put(root, null);

        boolean foundNode1 = root == node1;
        boolean foundNode2 = root == node2;

        // BFS visits nodes level by level while building the same child -> parent map.
        while (!queue.isEmpty() && (!foundNode1 || !foundNode2)) {
            NaryTreeNode current = queue.poll();

            for (NaryTreeNode child : current.children) {
                // Record the upward edge so we can later walk from a leaf to the root.
                parent.put(child, current);
                if (child == node1) {
                    foundNode1 = true;
                }
                if (child == node2) {
                    foundNode2 = true;
                }

                // Add child to the queue so its children are processed on later levels.
                queue.offer(child);
            }
        }
    }

    public static void main(String[] args) {
        NaryTreeNode root = new NaryTreeNode(1);
        NaryTreeNode node2 = new NaryTreeNode(2);
        NaryTreeNode node3 = new NaryTreeNode(3);
        NaryTreeNode node4 = new NaryTreeNode(4);
        NaryTreeNode node5 = new NaryTreeNode(5);
        NaryTreeNode node6 = new NaryTreeNode(6);
        NaryTreeNode node7 = new NaryTreeNode(7);

        root.children.add(node2);
        root.children.add(node3);
        root.children.add(node4);
        node2.children.add(node5);
        node2.children.add(node6);
        node4.children.add(node7);

        System.out.println(lowestCommonAncestor(root, node5, node6).val); // 2
        System.out.println(lowestCommonAncestor(root, node5, node7).val); // 1
        System.out.println(lowestCommonAncestorBfs(root, node5, node6).val); // 2
        System.out.println(lowestCommonAncestorBfs(root, node5, node7).val); // 1
    }
}
