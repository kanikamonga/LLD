package LLD.TennisCourt;

import java.util.*;

public class TennisClubScheduler {

    public static List<CourtAssignment> assignCourts(List<BookingRecord> bookings) {
        // Sort by start time
        bookings.sort(Comparator.comparingInt(b -> b.start));

        // Min-heap: (finish time, courtId)
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));

        List<CourtAssignment> result = new ArrayList<>();
        int nextCourtId = 1;

        for (BookingRecord booking : bookings) {
            if (!pq.isEmpty() && pq.peek()[0] <= booking.start) {
                // Reuse the court
                int[] top = pq.poll();
                result.add(new CourtAssignment(booking.id, top[1]));
                pq.offer(new int[]{booking.finish, top[1]});
            } else {
                // Allocate a new court
                result.add(new CourtAssignment(booking.id, nextCourtId));
                pq.offer(new int[]{booking.finish, nextCourtId});
                nextCourtId++;
            }
        }
        System.out.println("PQ Size = " + pq.size());
        return result;
    }

    public static List<CourtAssignment> assignCourtsWithMaintenance(
            List<BookingRecord> bookings,
            int maintenanceTime,
            int durability
    ) {
        bookings.sort(Comparator.comparingInt(b -> b.start));
        PriorityQueue<CourtState> pq = new PriorityQueue<>(Comparator.comparingInt(c -> c.freeAt));

        List<CourtAssignment> result = new ArrayList<>();
        int nextCourtId = 1;

        for (BookingRecord booking : bookings) {
            if (!pq.isEmpty() && pq.peek().freeAt <= booking.start) {
                CourtState court = pq.poll();
                result.add(new CourtAssignment(booking.id, court.courtId));

                court.usageCount++;
                court.freeAt = booking.finish;
                if (court.usageCount % durability == 0) {
                    court.freeAt += maintenanceTime;
                }
                pq.offer(court);
            } else {
                CourtState newCourt = new CourtState(nextCourtId, booking.finish, 1);
                result.add(new CourtAssignment(booking.id, nextCourtId));
                if (1 % durability == 0) {
                    newCourt.freeAt += maintenanceTime;
                }
                pq.offer(newCourt);
                nextCourtId++;
            }
        }
        return result;
    }

    public static int minCourtsRequired(List<BookingRecord> bookings) {
        int n = bookings.size();
        int[] starts = new int[n];
        int[] ends = new int[n];

        for (int i = 0; i < n; i++) {
            starts[i] = bookings.get(i).start;
            ends[i] = bookings.get(i).finish;
        }

        Arrays.sort(starts);
        Arrays.sort(ends);

        int i = 0, j = 0, courts = 0, maxCourts = 0;
        while (i < n && j < n) {
            if (starts[i] < ends[j]) {
                courts++;
                maxCourts = Math.max(maxCourts, courts);
                i++;
            } else {
                courts--;
                j++;
            }
        }
        return maxCourts;
    }

    // e) Check conflict
    public static boolean isConflict(BookingRecord a, BookingRecord b) {
        return !(a.finish <= b.start || b.finish <= a.start);
    }

    public static void main(String[] args) {
        List<BookingRecord> bookings = Arrays.asList(
                new BookingRecord(1, 1, 4),
                new BookingRecord(2, 2, 6),
                new BookingRecord(3, 5, 7),
                new BookingRecord(4, 6, 8)
        );

        System.out.println("=== Test A: Basic Court Assignment ===");
        List<CourtAssignment> resultA = assignCourts(bookings);
        resultA.forEach(System.out::println);

        System.out.println("\n=== Test B: Maintenance after every booking (X=1) ===");
        List<CourtAssignment> resultB = assignCourtsWithMaintenance(bookings, 2, 1);
        resultB.forEach(System.out::println);

        System.out.println("\n=== Test C: Maintenance after 2 bookings (Durability=2) ===");
        List<CourtAssignment> resultC = assignCourtsWithMaintenance(bookings, 2, 2);
        resultC.forEach(System.out::println);

        System.out.println("\n=== Test D: Minimum Courts Required ===");
        System.out.println("Min courts = " + minCourtsRequired(bookings));
        resultA = assignCourts(bookings);
        System.out.println("Min courts = " + resultA.get(resultA.size() - 1).courtId);

        System.out.println("\n=== Test E: Conflict Checking ===");
        BookingRecord a = new BookingRecord(5, 1, 4);
        BookingRecord b = new BookingRecord(6, 3, 5);
        BookingRecord c = new BookingRecord(7, 5, 7);
        System.out.println("Conflict (a,b): " + isConflict(a, b)); // true
        System.out.println("Conflict (a,c): " + isConflict(a, c)); // false
    }

}
