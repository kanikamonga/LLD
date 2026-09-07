package LLD.f1lastlaphero;

/**
 * Reproduces the worked example from the problem statement.
 */
public final class LastLapHeroDemo {
    public static void main(String[] args) {
        LastLapHeroTracker tracker = new LastLapHeroTracker(new AverageDeltaGainStrategy());

        // Observer pattern in action: subscribe to hero-change events instead of polling.
        tracker.addHeroChangeListener((previousHero, newHero) ->
                System.out.println("  [listener] Hero changed: " + previousHero + " -> " + newHero));

        Object[][] events = {
                {"Driver1", 100}, {"Driver2", 90}, {"Driver1", 110}, {"Driver2", 95}
        };

        for (Object[] event : events) {
            String driverId = (String) event[0];
            int lapTime = (Integer) event[1];
            tracker.updateLap(driverId, lapTime);
            System.out.printf("After (%s, %d) -> Last Lap Hero: %s%n",
                    driverId, lapTime, tracker.getLastLapHero());
        }

        System.out.println();
        System.out.println("Driver1 stats: " + tracker.getDriverStats("Driver1"));
        System.out.println("Driver2 stats: " + tracker.getDriverStats("Driver2"));
        System.out.println("Final Last Lap Hero: " + tracker.getLastLapHero());
    }
}
