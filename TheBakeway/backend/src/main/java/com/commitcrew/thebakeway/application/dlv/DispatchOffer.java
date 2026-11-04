package com.commitcrew.thebakeway.application.dlv;

import java.time.Instant;

/**
 * One active delivery offer sent to one courier for one READY order.
 *
 * The offer is separate from an assignment: Wiame's courier workflow decides
 * whether it is accepted or declined, and only acceptance may move the courier
 * from IDLE to ASSIGNED.
 */
public record DispatchOffer(
        String offerId,
        String orderId,
        String courierId,
        Instant offeredAt) {

    public DispatchOffer {
        requireNonBlank(offerId, "Offer ID is required.");
        requireNonBlank(orderId, "Order ID is required.");
        requireNonBlank(courierId, "Courier ID is required.");
        if (offeredAt == null) {
            throw new IllegalArgumentException("Offer time is required.");
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
