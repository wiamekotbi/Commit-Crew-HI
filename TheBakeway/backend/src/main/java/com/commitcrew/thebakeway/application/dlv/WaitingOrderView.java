package com.commitcrew.thebakeway.application.dlv;

/**
 * Privacy-limited operator view of an order awaiting courier dispatch.
 *
 * Customer identity, address, items, and pricing are intentionally omitted:
 * the operator only needs dispatch state to understand and resolve the wait.
 */
public record WaitingOrderView(
        String orderId,
        String bakeryId,
        String status,
        Integer queuePosition,
        String stalledCourierId,
        boolean reassignable) {
}
