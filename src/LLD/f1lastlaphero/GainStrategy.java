package LLD.f1lastlaphero;

/**
 * Strategy pattern: computes a driver's "performance gain" for their most
 * recently completed lap. Extracted as an interface because the problem
 * explicitly calls out that the gain FORMULA is a variation point ("raw
 * difference... but different interpretation possible") - e.g. a future
 * requirement might compare against the driver's BEST lap instead of their
 * average, or use a weighted/recency-biased average. The tracker itself
 * (index maintenance, thread-safety, hero lookup) must not change when the
 * formula changes, so the formula is isolated behind this abstraction.
 */
public interface GainStrategy {

    /**
     * @param totalTime   sum of all lap times completed so far (including the current lap)
     * @param totalLaps   number of laps completed so far (including the current lap)
     * @param lastLapTime the lap time just recorded
     * @return the performance gain for this driver's most recent lap
     */
    double computeGain(long totalTime, int totalLaps, int lastLapTime);

    String name();
}
