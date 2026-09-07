package LLD.EcommerceCheckout.service;

import LLD.EcommerceCheckout.inventory.InventoryService;
import LLD.EcommerceCheckout.model.CheckoutRequest;
import LLD.EcommerceCheckout.model.Order;
import LLD.EcommerceCheckout.payment.PaymentProcessor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checkout application service. In production, one database transaction should
 * cover order creation, inventory reservation, and state changes.
 */
public final class CheckoutService {
    private final InventoryService inventory;
    private final PaymentProcessor payments;
    private final int maxCaptureAttempts;
    private final Map<String, Order> ordersByIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, Object> idempotencyLocks = new ConcurrentHashMap<>();

    public CheckoutService(InventoryService inventory, PaymentProcessor payments) {
        this(inventory, payments, 3);
    }

    public CheckoutService(InventoryService inventory, PaymentProcessor payments,
                           int maxCaptureAttempts) {
        if (inventory == null || payments == null || maxCaptureAttempts < 1) {
            throw new IllegalArgumentException("Invalid checkout configuration");
        }
        this.inventory = inventory;
        this.payments = payments;
        this.maxCaptureAttempts = maxCaptureAttempts;
    }

    public Order checkout(CheckoutRequest request) {
        Object requestLock = idempotencyLocks.computeIfAbsent(
                request.idempotencyKey(), ignored -> new Object());
        synchronized (requestLock) {
            return checkoutOnce(request);
        }
    }

    private Order checkoutOnce(CheckoutRequest request) {
        Order previous = ordersByIdempotencyKey.get(request.idempotencyKey());
        if (previous != null) return previous;

        Order order = new Order(request.userId(), request.items());
        String reservationId = order.id();
        String authorizationId = null;
        boolean committed = false;
        try {
            inventory.reserve(reservationId, request.items());
            authorizationId = payments.authorize(order, request.paymentDetails());
            captureWithRetry(authorizationId, order, request);
            inventory.commit(reservationId);
            order.confirm();
            ordersByIdempotencyKey.put(request.idempotencyKey(), order);
            committed = true;
            return order;
        } finally {
            if (!committed) {
                if (authorizationId != null) {
                    payments.refund(authorizationId, order, request.paymentDetails());
                }
                inventory.release(reservationId);
                if (order.status() != Order.Status.CONFIRMED) {
                    order.cancel();
                }
            }
        }
    }

    private void captureWithRetry(String authorizationId, Order order,
                                  CheckoutRequest request) {
        if (authorizationId == null || authorizationId.trim().isEmpty()) {
            throw new IllegalStateException("Payment authorization failed");
        }
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxCaptureAttempts; attempt++) {
            try {
                payments.capture(authorizationId, order, request.paymentDetails());
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }
}
