package LLD.f1lastlaphero;

/**
 * Mutable per-driver running stats: total time/laps so far, plus the most
 * recently computed "performance gain" for that driver (computed via a
 * pluggable {@link GainStrategy} - see that interface for why the formula
 * itself is extracted rather than hardcoded here). Kept package-private -
 * only {@link LastLapHeroTracker} should mutate this.
 */
final class DriverStats {
    private final String driverId;
    private final GainStrategy gainStrategy;
    private long totalTime = 0L;
    private int totalLaps = 0;
    private int lastLapTime = 0;
    private double gain = Double.NEGATIVE_INFINITY; // undefined until first lap recorded

    DriverStats(String driverId, GainStrategy gainStrategy) {
        this.driverId = driverId;
        this.gainStrategy = gainStrategy;
    }

    String getDriverId() {
        return driverId;
    }

    int getTotalLaps() {
        return totalLaps;
    }

    double getAverageLapTime() {
        return totalLaps == 0 ? 0.0 : (double) totalTime / totalLaps;
    }

    int getLastLapTime() {
        return lastLapTime;
    }

    double getGain() {
        return gain;
    }

    /**
     * Records a newly completed lap and recomputes this driver's gain via
     * the injected {@link GainStrategy}.
     */
    void recordLap(int lapTime) {
        if (lapTime <= 0) {
            throw new IllegalArgumentException("lapTime must be > 0");
        }
        totalTime += lapTime;
        totalLaps += 1;
        lastLapTime = lapTime;
        gain = gainStrategy.computeGain(totalTime, totalLaps, lastLapTime);
    }
}
