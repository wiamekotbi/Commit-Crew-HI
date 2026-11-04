package com.commitcrew.thebakeway.application.dlv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Tracks offers awaiting a courier response.
 *
 * Separate indexes enforce both required exclusivity rules atomically: an
 * order cannot be offered twice, and a courier cannot hold two active offers.
 * Courier status alone cannot enforce this because a courier remains IDLE
 * until Wiame's acceptance command creates the actual assignment.
 */
@Component
public class ActiveDispatchOffers {

    private final Map<String, DispatchOffer> offersByOrderId = new LinkedHashMap<>();
    private final Map<String, DispatchOffer> offersByCourierId = new LinkedHashMap<>();

    /** Registers an active offer only when both its order and courier are free. */
    public synchronized DispatchOffer add(DispatchOffer offer) {
        if (offer == null) {
            throw new IllegalArgumentException("Dispatch offer is required.");
        }
        if (offersByOrderId.containsKey(offer.orderId())) {
            throw new IllegalStateException(
                    "Order already has an active offer: " + offer.orderId());
        }
        if (offersByCourierId.containsKey(offer.courierId())) {
            throw new IllegalStateException(
                    "Courier already has an active offer: " + offer.courierId());
        }

        offersByOrderId.put(offer.orderId(), offer);
        offersByCourierId.put(offer.courierId(), offer);
        return offer;
    }

    public synchronized Optional<DispatchOffer> findByOrderId(String orderId) {
        requireId(orderId, "Order ID is required.");
        return Optional.ofNullable(offersByOrderId.get(orderId));
    }

    public synchronized Optional<DispatchOffer> findByCourierId(String courierId) {
        requireId(courierId, "Courier ID is required.");
        return Optional.ofNullable(offersByCourierId.get(courierId));
    }

    public synchronized boolean isCourierReserved(String courierId) {
        return findByCourierId(courierId).isPresent();
    }

    /**
     * Removes both indexes together after acceptance, decline, cancellation,
     * or another transition that makes the order ineligible.
     */
    public synchronized Optional<DispatchOffer> removeByOrderId(String orderId) {
        requireId(orderId, "Order ID is required.");
        DispatchOffer removed = offersByOrderId.remove(orderId);
        if (removed != null) {
            offersByCourierId.remove(removed.courierId());
        }
        return Optional.ofNullable(removed);
    }

    /** Returns a defensive creation-order snapshot for inspection and tests. */
    public synchronized List<DispatchOffer> snapshot() {
        return List.copyOf(offersByOrderId.values());
    }

    public synchronized void clear() {
        offersByOrderId.clear();
        offersByCourierId.clear();
    }

    private void requireId(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
