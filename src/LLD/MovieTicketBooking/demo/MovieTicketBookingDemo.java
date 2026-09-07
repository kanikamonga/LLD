package LLD.MovieTicketBooking.demo;

import LLD.MovieTicketBooking.exception.PaymentFailedException;
import LLD.MovieTicketBooking.exception.SeatsUnavailableException;
import LLD.MovieTicketBooking.model.BookingSession;
import LLD.MovieTicketBooking.model.Movie;
import LLD.MovieTicketBooking.model.Screen;
import LLD.MovieTicketBooking.model.Seat;
import LLD.MovieTicketBooking.model.Show;
import LLD.MovieTicketBooking.model.User;
import LLD.MovieTicketBooking.payment.PaymentResult;
import LLD.MovieTicketBooking.payment.ScriptedPaymentGateway;
import LLD.MovieTicketBooking.service.MovieBookingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Runnable demonstration of successful, failed, expired, and concurrent bookings. */
public final class MovieTicketBookingDemo {
    public static void main(String[] args) throws Exception {
        successfulBooking();
        failedPaymentAndExplicitClose();
        overlappingSelection();
        timeout();
        concurrentSelection();
    }

    private static void successfulBooking() {
        MovieBookingService service = demoService(
                new ScriptedPaymentGateway(PaymentResult.SUCCESS));
        BookingSession session = service.startSession(user("U1"), "SHOW-1");
        service.selectSeats(session.id(), set("A1", "A2"));
        System.out.println("Case 1 - available before payment: "
                + labels(service.availableSeats("SHOW-1")));
        System.out.println("Case 1 - booking: " + service.pay(session.id()));
    }

    private static void failedPaymentAndExplicitClose() {
        MovieBookingService service = demoService(
                new ScriptedPaymentGateway(PaymentResult.FAILURE));
        BookingSession session = service.startSession(user("U1"), "SHOW-1");
        service.selectSeats(session.id(), set("A1"));
        try {
            service.pay(session.id());
        } catch (PaymentFailedException exception) {
            System.out.println("Case 2 - " + exception.getMessage());
        }
        System.out.println("Case 2 - after failure: "
                + labels(service.availableSeats("SHOW-1")));

        BookingSession closeSession = service.startSession(user("U1"), "SHOW-1");
        service.selectSeats(closeSession.id(), set("A2"));
        service.closeSession(closeSession.id());
        System.out.println("Case 2 - after explicit close: "
                + labels(service.availableSeats("SHOW-1")));
    }

    private static void overlappingSelection() {
        MovieBookingService service = demoService(
                new ScriptedPaymentGateway(PaymentResult.SUCCESS));
        BookingSession first = service.startSession(user("U1"), "SHOW-1");
        service.selectSeats(first.id(), set("A1", "A2"));
        BookingSession second = service.startSession(user("U2"), "SHOW-1");
        try {
            service.selectSeats(second.id(), set("A2", "A3"));
        } catch (SeatsUnavailableException exception) {
            System.out.println("Case 3 - " + exception.getMessage());
        }
    }

    private static void timeout() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        MovieBookingService service = new MovieBookingService(
                new ScriptedPaymentGateway(PaymentResult.SUCCESS), clock,
                Duration.ofMinutes(5), 0);
        service.addShow(sampleShow());
        BookingSession session = service.startSession(user("U1"), "SHOW-1");
        service.selectSeats(session.id(), set("A1"));
        clock.advance(Duration.ofMinutes(6));
        service.expireSessions();
        System.out.println("Timeout - available seats: "
                + labels(service.availableSeats("SHOW-1")));
    }

    private static void concurrentSelection() throws Exception {
        MovieBookingService service = demoService(
                new ScriptedPaymentGateway(PaymentResult.SUCCESS));
        BookingSession first = service.startSession(user("U1"), "SHOW-1");
        BookingSession second = service.startSession(user("U2"), "SHOW-1");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> result1 = executor.submit(() -> selectAtSameTime(
                service, first.id(), start, "U1"));
        Future<String> result2 = executor.submit(() -> selectAtSameTime(
                service, second.id(), start, "U2"));
        start.countDown();
        System.out.println("Concurrent selection - " + result1.get());
        System.out.println("Concurrent selection - " + result2.get());
        executor.shutdown();
    }

    private static String selectAtSameTime(MovieBookingService service, String sessionId,
                                           CountDownLatch start, String user) {
        try {
            start.await();
            service.selectSeats(sessionId, set("A1"));
            return user + " selected A1";
        } catch (Exception exception) {
            return user + " failed: " + exception.getMessage();
        }
    }

    private static MovieBookingService demoService(ScriptedPaymentGateway gateway) {
        MovieBookingService service = new MovieBookingService(
                gateway, Clock.systemUTC(), Duration.ofMinutes(10), 0);
        service.addShow(sampleShow());
        return service;
    }

    private static Show sampleShow() {
        List<Seat> seats = Arrays.asList(
                new Seat("A1", "A1"), new Seat("A2", "A2"),
                new Seat("A3", "A3"), new Seat("A4", "A4"));
        Screen screen = new Screen("SCREEN-1", seats);
        Movie movie = new Movie("MOVIE-1", "Inception", Duration.ofMinutes(148));
        return new Show("SHOW-1", movie, screen,
                LocalDateTime.of(2026, 1, 1, 18, 0));
    }

    private static User user(String id) { return new User(id, "User " + id); }
    private static HashSet<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
    private static List<String> labels(List<Seat> seats) {
        List<String> labels = new ArrayList<>();
        for (Seat seat : seats) labels.add(seat.label());
        return labels;
    }
    private MovieTicketBookingDemo() { }
}
