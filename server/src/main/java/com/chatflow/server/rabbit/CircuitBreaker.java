package com.chatflow.server.rabbit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CircuitBreaker {
    private final int failureThreshold;
    private final long cooldownMs;
    private final int halfOpenSuccessThreshold;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private volatile State state = State.CLOSED;

    private enum State { CLOSED, OPEN, HALF_OPEN }

    public CircuitBreaker(int failureThreshold, long cooldownMs) {
        this(failureThreshold, cooldownMs, 3);
    }

    public CircuitBreaker(int failureThreshold, long cooldownMs, int halfOpenSuccessThreshold) {
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;
    }

    public synchronized boolean allowRequest() {
        if (state == State.CLOSED) {
            return true;
        }

        if (state == State.OPEN) {
            // Check if cooldown period is over
            if (System.currentTimeMillis() - lastFailureTime.get() > cooldownMs) {
                state = State.HALF_OPEN;
                halfOpenSuccessCount.set(0);
                System.out.println("🔄 [CIRCUIT BREAKER] Transitioning to HALF_OPEN state, testing connection...");
                return true;
            } else {
                long remainingMs = cooldownMs - (System.currentTimeMillis() - lastFailureTime.get());
                if (failureCount.get() % 100 == 0) { // Log every 100 blocked requests
                    System.out.println("⏸️ [CIRCUIT BREAKER] Still OPEN, " + (remainingMs/1000) + "s remaining");
                }
                return false;
            }
        }

        // HALF_OPEN: allow requests to test if service recovered
        return true;
    }

    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN) {
            halfOpenSuccessCount.incrementAndGet();
            if (halfOpenSuccessCount.get() >= halfOpenSuccessThreshold) {
                state = State.CLOSED;
                failureCount.set(0);
                System.out.println("✅ [CIRCUIT BREAKER] Closed after " + halfOpenSuccessCount.get() + " successful requests");
            }
        } else if (state == State.CLOSED) {
            // Reset failure count on success
            if (failureCount.get() > 0) {
                failureCount.set(0);
            }
        }
    }

    public synchronized void recordFailure() {
        int currentFailures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (state == State.HALF_OPEN) {
            // If failure in half-open state, go back to open
            state = State.OPEN;
            System.out.println("⚠️ [CIRCUIT BREAKER] Back to OPEN state after failure in HALF_OPEN");
        } else if (currentFailures >= failureThreshold && state == State.CLOSED) {
            state = State.OPEN;
            System.out.println("[CIRCUIT BREAKER] OPENED after " + currentFailures + " consecutive failures!");
        }
    }

    public State getState() {
        return state;
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}