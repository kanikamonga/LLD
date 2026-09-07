package DSA.Amazon;

import java.util.List;

/*
 * Singly linked-list operations:
 *
 * createLinkedList:       O(n)
 * removeAllOccurrences:   O(n)
 * insertAtPosition:       O(n)
 *
 * Positions are zero-based. If the requested insertion position is greater
 * than the current length, the new node is appended to the end.
 */
public class SinglyLinkedListOperations {

    public static class ListNode {
        public int value;
        public ListNode next;

        public ListNode(int value) {
            this.value = value;
        }
    }

    public static ListNode createLinkedList(List<Integer> values) {
        if (values == null) {
            throw new IllegalArgumentException("Values cannot be null");
        }

        ListNode dummy = new ListNode(0);
        ListNode tail = dummy;

        for (int value : values) {
            tail.next = new ListNode(value);
            tail = tail.next;
        }

        return dummy.next;
    }

    public static ListNode removeAllOccurrences(
            ListNode head, int value) {
        // A dummy node handles removals from the head uniformly.
        ListNode dummy = new ListNode(0);
        dummy.next = head;

        ListNode previous = dummy;
        ListNode current = head;

        while (current != null) {
            if (current.value == value) {
                previous.next = current.next;
            } else {
                previous = current;
            }
            current = current.next;
        }

        return dummy.next;
    }

    public static ListNode insertAtPosition(
            ListNode head, int value, int position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }

        ListNode newNode = new ListNode(value);
        if (position == 0) {
            newNode.next = head;
            return newNode;
        }

        ListNode current = head;
        int index = 0;

        // Stop at the node immediately before the target position, or at the
        // current tail when the position is beyond the list length.
        while (current != null && current.next != null && index < position - 1) {
            current = current.next;
            index++;
        }

        if (current == null) {
            // The list is empty and position is greater than zero.
            return newNode;
        }

        newNode.next = current.next;
        current.next = newNode;
        return head;
    }

    public static String toString(ListNode head) {
        StringBuilder result = new StringBuilder();
        while (head != null) {
            result.append(head.value).append(" -> ");
            head = head.next;
        }
        result.append("null");
        return result.toString();
    }

    public static void main(String[] args) {
        ListNode head = createLinkedList(
                java.util.Arrays.asList(1, 2, 3, 2, 4));
        head = removeAllOccurrences(head, 2);
        head = insertAtPosition(head, 5, 2);

        System.out.println(toString(head));
        // 1 -> 3 -> 5 -> 4 -> null
    }
}
