package com.commitcrew.thebakeway.domain.courier;

import com.commitcrew.thebakeway.domain.location.Coordinates;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

    // The configured starting location is used by the selection strategy.
    // Courier duty and assignment eligibility still come exclusively from status.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "longitude"))
    })
    private Coordinates coordinates;

    protected Courier() {
    }

    public Courier(String courierId) {
        requireNonBlank(courierId, "Courier ID is required");
        this.courierId = courierId;
        this.status = CourierStatus.OFFLINE;
    }

    /**
     * Creates a configured courier at a known location. The existing
     * constructor remains available so Wiame's lifecycle callers and tests do
     * not need to change until the initial-state configuration is migrated.
     */
    public Courier(String courierId, Coordinates coordinates) {
        this(courierId);
        if (coordinates == null) {
            throw new IllegalArgumentException("Courier coordinates cannot be null.");
        }
        this.coordinates = coordinates;
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

    /** Accepts the active dispatch offer for one order. */
    public void acceptOffer(String orderId) {
        assignOrder(orderId);
    }

    /** Declining an offer does not change an available courier's status. */
    public void declineOffer() {
        requireStatus(CourierStatus.IDLE, "decline an order");
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

    /**
     * Ends the previous assignment only after an assigned courier has gone
     * offline and left this exact order stalled. Operator reassignment calls
     * this before asking dispatch to seek a replacement courier.
     *
     * Keeping this rule in the domain entity prevents callers from clearing a
     * different order or taking work away from an active courier.
     */
    public void releaseStalledOrder(String orderId) {
        requireNonBlank(orderId, "Order ID is required");
        requireStatus(CourierStatus.OFFLINE, "release a stalled order");
        if (!orderId.equals(stalledOrderId)) {
            throw new IllegalStateException(
                    "Courier does not hold the stalled order: " + orderId);
        }

        stalledOrderId = null;
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

    public Coordinates getCoordinates() {
        return coordinates;
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
