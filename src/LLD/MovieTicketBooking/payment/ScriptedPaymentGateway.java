package LLD.MovieTicketBooking.payment;

import LLD.MovieTicketBooking.model.BookingSession;
import LLD.MovieTicketBooking.model.User;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic payment fake used by the executable demonstrations. */
public final class ScriptedPaymentGateway implements PaymentGateway {
    private final List<PaymentResult> results;
    private final AtomicInteger attempt = new AtomicInteger();

    public ScriptedPaymentGateway(PaymentResult... results) {
        this.results = Arrays.asList(results);
    }

    @Override
    public PaymentResult pay(User user, BookingSession session) {
        int index = attempt.getAndIncrement();
        return index < results.size() ? results.get(index) : PaymentResult.FAILURE;
    }
}
