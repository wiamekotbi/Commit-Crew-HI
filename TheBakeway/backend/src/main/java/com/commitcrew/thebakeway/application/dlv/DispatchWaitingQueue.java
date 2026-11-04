package com.commitcrew.thebakeway.application.dlv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * In-memory FIFO index of READY orders currently waiting for courier dispatch.
 *
 * A {@link LinkedHashSet} preserves insertion order while refusing duplicate
 * order IDs. The queue stores references only; OrderRegistry remains the source
 * of truth for whether an order still exists and is still READY when dispatch
 * retries it.
 */
@Component
public class DispatchWaitingQueue {

    private final Set<String> orderIds = new LinkedHashSet<>();

    /**
     * Adds an order once, retaining its original FIFO position if a later
     * dispatch attempt tries to enqueue it again.
     *
     * @return true when a new queue entry was added
     */
    public synchronized boolean enqueue(String orderId) {
        requireOrderId(orderId);
        return orderIds.add(orderId);
    }

    /** Returns the oldest waiting order without removing it. */
    public synchronized Optional<String> peek() {
        return orderIds.stream().findFirst();
    }

    /**
     * Removes an order after it receives an offer or becomes ineligible through
     * cancellation or another lifecycle change.
     */
    public synchronized boolean remove(String orderId) {
        requireOrderId(orderId);
        return orderIds.remove(orderId);
    }

    public synchronized boolean contains(String orderId) {
        requireOrderId(orderId);
        return orderIds.contains(orderId);
    }

    /** Returns a defensive FIFO snapshot suitable for retry iteration or UI use. */
    public synchronized List<String> snapshot() {
        return List.copyOf(new ArrayList<>(orderIds));
    }

    /** Clears transient dispatch state during the existing Reset operation. */
    public synchronized void clear() {
        orderIds.clear();
    }

    private void requireOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID is required.");
        }
    }
}
