package LLD;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

enum CircuitBreakerState { CLOSED, OPEN, HALF_OPEN }

class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String message) { super(message); }
}

public class CircuitBreaker {
    private final long resetTimeout;
    private final int failureThreshold;
    private final int successThreshold;

    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private int failureCount = 0;
    private int successCount = 0;
    private long lastFailureTime = 0;

    // The Lock System
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public CircuitBreaker(long resetTimeout, int failureThreshold, int successThreshold) {
        this.resetTimeout = resetTimeout;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
    }

    public <T> T execute(Supplier<T> action) {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException("Circuit Breaker is OPEN. Request rejected.");
        }

        try {
            T result = action.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e; 
        }
    }

    /**
     * READ LOCK primarily. Thousands of threads can do this simultaneously.
     * Upgrades to WRITE LOCK only if a state change is required.
     */
    private boolean allowRequest() {
        readLock.lock();
        try {
            if (state == CircuitBreakerState.CLOSED || state == CircuitBreakerState.HALF_OPEN) {
                return true;
            }
            if (System.currentTimeMillis() - lastFailureTime < resetTimeout) {
                return false; // Still OPEN and timeout hasn't elapsed
            }
        } finally {
            readLock.unlock();
        }

        // Lock Upgrade: We need to transition from OPEN to HALF_OPEN
        writeLock.lock();
        try {
            // Double-check state in case another thread already upgraded it while we waited for the write lock
            if (state == CircuitBreakerState.OPEN && System.currentTimeMillis() - lastFailureTime >= resetTimeout) {
                state = CircuitBreakerState.HALF_OPEN;
                successCount = 0;
            }
            return state == CircuitBreakerState.HALF_OPEN;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * WRITE LOCK: Mutates internal counters and state. Exclusive access required.
     */
    private void recordSuccess() {
        writeLock.lock();
        try {
            if (state == CircuitBreakerState.HALF_OPEN) {
                successCount++;
                if (successCount >= successThreshold) {
                    System.out.println("Service recovered. Circuit resetting to CLOSED.");
                    state = CircuitBreakerState.CLOSED;
                    failureCount = 0;
                    successCount = 0;
                }
            } else if (state == CircuitBreakerState.CLOSED) {
                failureCount = 0;
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * WRITE LOCK: Mutates failure counts and trips the circuit. Exclusive access required.
     */
    private void recordFailure() {
        writeLock.lock();
        try {
            lastFailureTime = System.currentTimeMillis();
            failureCount++;

            if (state == CircuitBreakerState.CLOSED && failureCount >= failureThreshold) {
                System.out.println("Threshold reached. Circuit tripping to OPEN.");
                state = CircuitBreakerState.OPEN;
            } else if (state == CircuitBreakerState.HALF_OPEN) {
                System.out.println("Failure during HALF_OPEN. Circuit tripping back to OPEN.");
                state = CircuitBreakerState.OPEN;
                successCount = 0;
            }
        } finally {
            writeLock.unlock();
        }
    }
}