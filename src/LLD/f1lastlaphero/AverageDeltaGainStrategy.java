package LLD.f1lastlaphero;

/**
 * The formula given in the problem statement: gain = lastLapTime - average(totalTime / totalLaps),
 * where the average includes the lap just completed.
 */
public final class AverageDeltaGainStrategy implements GainStrategy {

    @Override
    public double computeGain(long totalTime, int totalLaps, int lastLapTime) {
        double average = totalLaps == 0 ? 0.0 : (double) totalTime / totalLaps;
        return lastLapTime - average;
    }

    @Override
    public String name() {
        return "Average-delta";
    }
}
