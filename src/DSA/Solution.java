package DSA;

import java.util.*;

class Solution {
    public int minMeetingRooms(int[][] intervals) {

        if (intervals == null || intervals.length == 0) {
            return 0;
        }

        // Sort meetings by start time
        Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));

        // Min heap containing end times
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();

        int maxRooms = 0;

        for (int[] meeting : intervals) {

            int start = meeting[0];
            int end = meeting[1];

            // Reuse a room if the earliest meeting has ended
            if (!minHeap.isEmpty() && minHeap.peek() <= start) {
                minHeap.poll();
            }

            // Allocate room for current meeting
            minHeap.offer(end);

            maxRooms = Math.max(maxRooms, minHeap.size());
        }

        return maxRooms;
    }
}