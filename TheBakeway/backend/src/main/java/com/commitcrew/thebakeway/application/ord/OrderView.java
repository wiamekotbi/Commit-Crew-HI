package com.commitcrew.thebakeway.application.ord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

// An order as shown to customers, staff and the operator. queuePosition is 1 for the first
// order of the bakery's preparation queue and null when the order is not queued.
// history is only filled in when one order is opened (UC-ORD-12, UC-ORD-13).
public record OrderView(
        String orderId,
        String bakeryId,
        String customerId,
        String recipientName,
        String deliveryAddress,
        String status,
        String rejectionReason,
        List<Line> lines,
        double total,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer queuePosition,
        List<Transition> history) {

    public record Line(String menuItemId, String itemName, double unitPrice, int quantity, double subtotal) {
    }

    public record Transition(String eventId, String previousState, String newState, Instant timestamp) {
    }
}
