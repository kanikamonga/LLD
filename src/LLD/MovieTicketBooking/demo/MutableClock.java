package LLD.MovieTicketBooking.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/** Test clock that can be advanced to demonstrate session expiration. */
public final class MutableClock extends Clock {
    private Instant instant;

    public MutableClock(Instant instant) { this.instant = instant; }
    public synchronized void advance(Duration duration) { instant = instant.plus(duration); }
    @Override public synchronized ZoneOffset getZone() { return ZoneOffset.UTC; }
    @Override public synchronized Clock withZone(java.time.ZoneId zone) { return this; }
    @Override public synchronized Instant instant() { return instant; }
}
