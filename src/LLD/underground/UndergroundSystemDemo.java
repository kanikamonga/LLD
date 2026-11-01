package LLD.underground;

public final class UndergroundSystemDemo {
    public static void main(String[] args) {
        UndergroundSystem system = new UndergroundSystem();
        system.checkIn(1, "Central", 10);
        system.checkIn(2, "Central", 12);
        system.checkOut(1, "Airport", 30);
        System.out.println("Central -> Airport: " + system.getAverageTime("Central", "Airport"));
        system.checkOut(2, "Airport", 42);
        System.out.println("Central -> Airport: " + system.getAverageTime("Central", "Airport"));

        system.checkIn(1, "Airport", 50);
        system.checkOut(1, "Central", 65);
        System.out.println("Airport -> Central: " + system.getAverageTime("Airport", "Central"));
    }
}
