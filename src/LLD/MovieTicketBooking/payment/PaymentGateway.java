package LLD.MovieTicketBooking.payment;

import LLD.MovieTicketBooking.model.BookingSession;
import LLD.MovieTicketBooking.model.User;

/** Abstraction for payment providers used by the booking service. */
public interface PaymentGateway {
    PaymentResult pay(User user, BookingSession session);
}
