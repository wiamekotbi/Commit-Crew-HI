package com.commitcrew.thebakeway.application.ord;

// What an order command returns: the new state and the event id that recorded it (R-ORD-05).
// previousState is null when an order is placed; queuePosition is set only when the order is in the queue.
public record OrderTransitionResult(
        String orderId,
        String previousState,
        String newState,
        String eventId,
        Integer queuePosition) {
}
