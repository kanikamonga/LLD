package LLD.underground;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Immutable running aggregate; owns arithmetic and cannot expose mutable counters. */
public final class RouteStatistics {
    private final BigInteger totalMinutes;
    private final BigInteger tripCount;

    public RouteStatistics() {
        this(BigInteger.ZERO, BigInteger.ZERO);
    }

    private RouteStatistics(BigInteger totalMinutes, BigInteger tripCount) {
        this.totalMinutes = totalMinutes;
        this.tripCount = tripCount;
    }

    public RouteStatistics addDuration(long durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Trip duration must be positive");
        }
        return new RouteStatistics(totalMinutes.add(BigInteger.valueOf(durationMinutes)),
                tripCount.add(BigInteger.ONE));
    }

    public double average() {
        if (tripCount.signum() == 0) {
            throw new IllegalStateException("Cannot average statistics with no completed trips");
        }
        return new BigDecimal(totalMinutes)
                .divide(new BigDecimal(tripCount), MathContext.DECIMAL128)
                .doubleValue();
    }
}
