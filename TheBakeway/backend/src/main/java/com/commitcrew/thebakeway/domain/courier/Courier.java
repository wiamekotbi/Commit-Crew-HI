package com.commitcrew.thebakeway.domain.courier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "couriers")
public class Courier {

    @Id
    @Column(name = "courier_id", nullable = false, updatable = false)
    private String courierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourierStatus status;

    @Column(name = "current_order_id")
    private String currentOrderId;

    /**
     * Remembers an assignment abandoned when an assigned courier goes offline.
     * Dispatch can later use this reference to detect and reassign that stalled order.
     */
    @Column(name = "stalled_order_id")
    private String stalledOrderId;

    protected Courier() {
    }

    public Courier(String courierId) {
        requireNonBlank(courierId, "Courier ID is required");
        this.courierId = courierId;
        this.status = CourierStatus.OFFLINE;
    }

    public void goOnDuty() {
        requireStatus(CourierStatus.OFFLINE, "go on duty");
        status = CourierStatus.IDLE;
    }

    public void goOffDuty() {
        if (status == CourierStatus.CARRYING) {
            throw new IllegalStateException(
                    "A courier carrying an order cannot go offline (R-DLV-03)");
        }
        if (status == CourierStatus.ASSIGNED) {
            stalledOrderId = currentOrderId;
            currentOrderId = null;
            status = CourierStatus.OFFLINE;
            return;
        }

        requireStatus(CourierStatus.IDLE, "go off duty");
        status = CourierStatus.OFFLINE;
    }

    /**
     * Records one accepted assignment. Dispatch decides which order is offered;
     * this entity only enforces the courier's capacity and status invariants.
     */
    public void assignOrder(String orderId) {
        requireNonBlank(orderId, "Order ID is required");
        requireStatus(CourierStatus.IDLE, "accept an order");
        if (currentOrderId != null) {
            throw new IllegalStateException("Courier already has an order");
        }

        currentOrderId = orderId;
        status = CourierStatus.ASSIGNED;
    }

    public void markPickedUp() {
        requireStatus(CourierStatus.ASSIGNED, "pick up an order");
        if (currentOrderId == null) {
            throw new IllegalStateException("Courier has no assigned order");
        }
        status = CourierStatus.CARRYING;
    }

    public void markDelivered() {
        requireStatus(CourierStatus.CARRYING, "deliver an order");
        status = CourierStatus.IDLE;
        currentOrderId = null;
    }

    public String getCourierId() {
        return courierId;
    }

    public CourierStatus getStatus() {
        return status;
    }

    public String getCurrentOrderId() {
        return currentOrderId;
    }

    public String getStalledOrderId() {
        return stalledOrderId;
    }

    private void requireStatus(CourierStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Courier cannot " + action + " while " + status);
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
