package LLD.MovieTicketBooking.service;

import LLD.MovieTicketBooking.exception.PaymentFailedException;
import LLD.MovieTicketBooking.exception.SessionExpiredException;
import LLD.MovieTicketBooking.model.Booking;
import LLD.MovieTicketBooking.model.BookingSession;
import LLD.MovieTicketBooking.model.Seat;
import LLD.MovieTicketBooking.model.Show;
import LLD.MovieTicketBooking.model.User;
import LLD.MovieTicketBooking.payment.PaymentGateway;
import LLD.MovieTicketBooking.payment.PaymentResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Orchestrates shows, temporary sessions, payments, and booking confirmations. */
public final class MovieBookingService {
    private final PaymentGateway paymentGateway;
    private final Clock clock;
    private final Duration sessionTimeout;
    private final int maxPaymentRetries;
    private final Map<String, Show> shows = new HashMap<>();
    private final Map<String, BookingSession> sessions = new HashMap<>();
    private final Map<String, Booking> bookings = new HashMap<>();

    public MovieBookingService(PaymentGateway paymentGateway, Clock clock,
                               Duration sessionTimeout, int maxPaymentRetries) {
        if (paymentGateway == null || clock == null || sessionTimeout == null
                || sessionTimeout.isZero() || sessionTimeout.isNegative()
                || maxPaymentRetries < 0) {
            throw new IllegalArgumentException("Invalid booking configuration");
        }
        this.paymentGateway = paymentGateway;
        this.clock = clock;
        this.sessionTimeout = sessionTimeout;
        this.maxPaymentRetries = maxPaymentRetries;
    }

    public synchronized void addShow(Show show) {
        if (shows.put(show.id(), show) != null) {
            throw new IllegalArgumentException("Show already exists: " + show.id());
        }
    }

    public synchronized List<Show> availableShows() {
        return Collections.unmodifiableList(new ArrayList<>(shows.values()));
    }

    public synchronized List<Seat> availableSeats(String showId) {
        expireSessions();
        return show(showId).availableSeats();
    }

    public synchronized BookingSession startSession(User user, String showId) {
        expireSessions();
        Show show = show(showId);
        BookingSession session = new BookingSession(
                UUID.randomUUID().toString(), user, show,
                clock.instant().plus(sessionTimeout));
        sessions.put(session.id(), session);
        return session;
    }

    public synchronized void selectSeats(String sessionId, Set<String> seatIds) {
        expireSessions();
        BookingSession session = activeSession(sessionId);
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("At least one seat must be selected");
        }
        session.show().holdSeats(session.id(), seatIds);
        session.addSelectedSeats(seatIds);
    }

    public synchronized Booking pay(String sessionId) {
        expireSessions();
        BookingSession session = activeSession(sessionId);
        if (session.selectedSeatIds().isEmpty()) {
            throw new IllegalStateException("Select seats before payment");
        }
        session.incrementPaymentAttempts();
        if (paymentGateway.pay(session.user(), session) == PaymentResult.SUCCESS) {
            session.show().bookSeats(session.id(), session.selectedSeatIds());
            Booking booking = new Booking(session.id(), session.user().id(),
                    session.show().id(), session.selectedSeatIds());
            bookings.put(booking.id(), booking);
            session.close();
            sessions.remove(session.id());
            return booking;
        }
        if (session.paymentAttempts() > maxPaymentRetries) {
            releaseAndClose(session);
            throw new PaymentFailedException(
                    "Payment failed after maximum retries; seats are available again");
        }
        throw new PaymentFailedException("Payment failed; retry is available");
    }

    public synchronized void closeSession(String sessionId) {
        expireSessions();
        releaseAndClose(activeSession(sessionId));
    }

    public synchronized void expireSessions() {
        List<BookingSession> expired = new ArrayList<>();
        for (BookingSession session : sessions.values()) {
            if (session.isExpired(clock.instant())) {
                expired.add(session);
            }
        }
        for (BookingSession session : expired) releaseAndClose(session);
    }

    private void releaseAndClose(BookingSession session) {
        session.show().releaseSeats(session.id(), session.selectedSeatIds());
        session.close();
        sessions.remove(session.id());
    }

    private BookingSession activeSession(String sessionId) {
        BookingSession session = sessions.get(sessionId);
        if (session == null || session.isClosed()) {
            throw new IllegalStateException("Booking session is not active");
        }
        if (session.isExpired(clock.instant())) {
            releaseAndClose(session);
            throw new SessionExpiredException("Booking session expired");
        }
        return session;
    }

    private Show show(String showId) {
        Show show = shows.get(showId);
        if (show == null) throw new IllegalArgumentException("Show not found: " + showId);
        return show;
    }
}
