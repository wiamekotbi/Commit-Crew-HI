package com.commitcrew.thebakeway.domain.order;

// Stored as these exact lowercase strings (same convention as MenuItemStatus)
public enum OrderStatus {
    placed,     // Waiting for the bakery to accept or reject it
    accepted,   // Waiting in the bakery's preparation queue (FIFO)
    preparing,  // Actively being prepared (leaves the queue)
    ready,      // Finished, waiting for a courier
    picked_up,  // Picked up by a courier
    delivered,  // Delivered to the customer (final)
    rejected,   // Rejected by the bakery (final)
    cancelled   // Cancelled by the customer or an operator (final)
}
