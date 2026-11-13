package com.commitcrew.thebakeway.domain.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.location.Coordinates;

class CourierTest {

    private static final String COURIER_ID = "courier-1";
    private static final String ORDER_ID = "order-1";

    @Test
    void newCourierStartsOfflineWithoutAnOrder() {
        Courier courier = new Courier(COURIER_ID);

        assertEquals(COURIER_ID, courier.getCourierId());
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
        assertNull(courier.getCurrentOrderId());
        assertNull(courier.getStalledOrderId());
    }

    @Test
    void configuredCourierKeepsCoordinatesAndStartsOffline() {
        // Dispatch needs the configured location, but adding it must not alter
        // Wiame's courier lifecycle: a newly loaded courier still starts offline.
        Coordinates coordinates = new Coordinates(45.5019, -73.5674);
        Courier courier = new Courier(COURIER_ID, coordinates);

        assertSame(coordinates, courier.getCoordinates());
        assertEquals(45.5019, courier.getCoordinates().getLatitude());
        assertEquals(-73.5674, courier.getCoordinates().getLongitude());
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
        assertNull(courier.getCurrentOrderId());
    }

    @Test
    void configuredCourierRejectsMissingCoordinates() {
        // A courier without a location cannot participate safely in nearest-
        // courier selection, so configured construction refuses a null value.
        assertThrows(IllegalArgumentException.class,
                () -> new Courier(COURIER_ID, null));
    }

    @Test
    void rejectsMissingCourierId() {
        assertThrows(IllegalArgumentException.class, () -> new Courier(null));
        assertThrows(IllegalArgumentException.class, () -> new Courier(" "));
    }

    @Test
    void courierGoesOnAndOffDuty() {
        Courier courier = new Courier(COURIER_ID);

        courier.goOnDuty();
        assertEquals(CourierStatus.IDLE, courier.getStatus());

        courier.goOffDuty();
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
    }

    @Test
    void rejectsInvalidDutyTransitions() {
        Courier courier = new Courier(COURIER_ID);

        assertThrows(IllegalStateException.class, courier::goOffDuty);

        courier.goOnDuty();
        assertThrows(IllegalStateException.class, courier::goOnDuty);
    }

    @Test
    void assignedCourierMayGoOfflineAndLeavesAStalledOrderReference() {
        Courier courier = assignedCourier();

        courier.goOffDuty();

        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
        assertNull(courier.getCurrentOrderId());
        assertEquals(ORDER_ID, courier.getStalledOrderId());
    }

    @Test
    void operatorReassignmentMayReleaseTheRecordedStalledOrder() {
        Courier courier = assignedCourier();
        courier.goOffDuty();

        courier.releaseStalledOrder(ORDER_ID);

        // Releasing the abandoned assignment does not put the old courier back
        // on duty; dispatch will independently select another eligible courier.
        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
        assertNull(courier.getCurrentOrderId());
        assertNull(courier.getStalledOrderId());
    }

    @Test
    void releasingAnotherOrderIsRejectedWithoutChangingTheStalledAssignment() {
        Courier courier = assignedCourier();
        courier.goOffDuty();

        assertThrows(IllegalStateException.class,
                () -> courier.releaseStalledOrder("order-2"));

        assertEquals(CourierStatus.OFFLINE, courier.getStatus());
        assertEquals(ORDER_ID, courier.getStalledOrderId());
    }

    @Test
    void activeCourierCannotReleaseAnAssignmentAsStalled() {
        Courier courier = assignedCourier();

        assertThrows(IllegalStateException.class,
                () -> courier.releaseStalledOrder(ORDER_ID));

        assertEquals(CourierStatus.ASSIGNED, courier.getStatus());
        assertEquals(ORDER_ID, courier.getCurrentOrderId());
        assertNull(courier.getStalledOrderId());
    }

    @Test
    void carryingCourierCannotGoOffline() {
        Courier courier = assignedCourier();
        courier.markPickedUp();

        assertThrows(IllegalStateException.class, courier::goOffDuty);
        assertEquals(CourierStatus.CARRYING, courier.getStatus());
        assertEquals(ORDER_ID, courier.getCurrentOrderId());
    }

    @Test
    void pickupAndDeliveryFollowTheCourierLifecycle() {
        Courier courier = assignedCourier();

        courier.markPickedUp();
        assertEquals(CourierStatus.CARRYING, courier.getStatus());
        assertEquals(ORDER_ID, courier.getCurrentOrderId());

        courier.markDelivered();
        assertEquals(CourierStatus.IDLE, courier.getStatus());
        assertNull(courier.getCurrentOrderId());
    }

    @Test
    void courierCannotHoldMoreThanOneOrder() {
        Courier courier = assignedCourier();

        assertThrows(IllegalStateException.class, () -> courier.assignOrder("order-2"));
        assertEquals(ORDER_ID, courier.getCurrentOrderId());
        assertEquals(CourierStatus.ASSIGNED, courier.getStatus());
    }

    @Test
    void acceptingOfferAssignsIdleCourier() {
        Courier courier = new Courier(COURIER_ID);
        courier.goOnDuty();

        courier.acceptOffer(ORDER_ID);

        assertEquals(CourierStatus.ASSIGNED, courier.getStatus());
        assertEquals(ORDER_ID, courier.getCurrentOrderId());
    }

    @Test
    void decliningOfferLeavesCourierIdle() {
        Courier courier = new Courier(COURIER_ID);
        courier.goOnDuty();

        courier.declineOffer();

        assertEquals(CourierStatus.IDLE, courier.getStatus());
        assertNull(courier.getCurrentOrderId());
    }

    @Test
    void assignedCourierCannotAcceptOrDeclineAnotherOffer() {
        Courier courier = assignedCourier();

        assertThrows(IllegalStateException.class,
                () -> courier.acceptOffer("order-2"));
        assertThrows(IllegalStateException.class, courier::declineOffer);
        assertEquals(CourierStatus.ASSIGNED, courier.getStatus());
        assertEquals(ORDER_ID, courier.getCurrentOrderId());
    }

    @Test
    void rejectsAssignmentUnlessCourierIsIdle() {
        Courier courier = new Courier(COURIER_ID);

        assertThrows(IllegalStateException.class, () -> courier.assignOrder(ORDER_ID));
        assertThrows(IllegalArgumentException.class, () -> courier.assignOrder(" "));
    }

    @Test
    void rejectsPickupUnlessAssigned() {
        Courier courier = new Courier(COURIER_ID);
        courier.goOnDuty();

        assertThrows(IllegalStateException.class, courier::markPickedUp);
        assertEquals(CourierStatus.IDLE, courier.getStatus());
    }

    @Test
    void rejectsDeliveryUnlessCarrying() {
        Courier courier = assignedCourier();

        assertThrows(IllegalStateException.class, courier::markDelivered);
        assertEquals(CourierStatus.ASSIGNED, courier.getStatus());
        assertEquals(ORDER_ID, courier.getCurrentOrderId());
    }

    @Test
    void cancelledOrderIsReleasedFromAnAssignedCourier() {
        Courier courier = assignedCourier();

        courier.releaseCancelledOrder(ORDER_ID);

        assertEquals(CourierStatus.IDLE, courier.getStatus());
        assertNull(courier.getCurrentOrderId());
    }

    @Test
    void releasingACancelledOrderRequiresThatExactAssignedOrder() {
        Courier courier = assignedCourier();

        assertThrows(IllegalStateException.class, () -> courier.releaseCancelledOrder("other-order"));
        courier.markPickedUp();
        assertThrows(IllegalStateException.class, () -> courier.releaseCancelledOrder(ORDER_ID));

        assertEquals(CourierStatus.CARRYING, courier.getStatus());
        assertEquals(ORDER_ID, courier.getCurrentOrderId());
    }

    private Courier assignedCourier() {
        Courier courier = new Courier(COURIER_ID);
        courier.goOnDuty();
        courier.assignOrder(ORDER_ID);
        return courier;
    }
}
