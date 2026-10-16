package com.commitcrew.thebakeway.domain.order;

public enum OrderStatus {
    ACCEPTED,   // Waiting in the kitchen preparation queue (FIFO)
    PREPARING,  // Actively being prepared by bakers (leaves the queue)
    READY,      // Finished baking, ready for delivery or pickup
    COMPLETED,  // Successfully delivered or picked up
    CANCELLED   // Cancelled by customer or staff
}