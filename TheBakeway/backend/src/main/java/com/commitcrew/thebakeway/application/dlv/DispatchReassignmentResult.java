package com.commitcrew.thebakeway.application.dlv;

/** Result returned after an operator releases one stalled assignment. */
public record DispatchReassignmentResult(
        String orderId,
        String previousCourierId,
        String offeredCourierId,
        boolean waiting) {
}
