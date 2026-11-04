package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ActiveDispatchOffersTest {

    private final ActiveDispatchOffers activeOffers = new ActiveDispatchOffers();

    @Test
    void registersAndFindsOfferByOrderAndCourier() {
        DispatchOffer offer = offer("offer-1", "order-1", "courier-1");

        assertEquals(offer, activeOffers.add(offer));
        assertEquals(offer, activeOffers.findByOrderId("order-1").orElseThrow());
        assertEquals(offer, activeOffers.findByCourierId("courier-1").orElseThrow());
        assertTrue(activeOffers.isCourierReserved("courier-1"));
        assertEquals(java.util.Set.of("courier-1"), activeOffers.reservedCourierIds());
    }

    @Test
    void refusesSecondOfferForSameOrder() {
        activeOffers.add(offer("offer-1", "order-1", "courier-1"));

        assertThrows(IllegalStateException.class,
                () -> activeOffers.add(offer("offer-2", "order-1", "courier-2")));
        assertEquals(1, activeOffers.snapshot().size());
    }

    @Test
    void refusesSecondOfferForSameCourier() {
        activeOffers.add(offer("offer-1", "order-1", "courier-1"));

        assertThrows(IllegalStateException.class,
                () -> activeOffers.add(offer("offer-2", "order-2", "courier-1")));
        assertEquals(1, activeOffers.snapshot().size());
    }

    @Test
    void removalReleasesBothOrderAndCourier() {
        DispatchOffer offer = offer("offer-1", "order-1", "courier-1");
        activeOffers.add(offer);

        assertEquals(offer, activeOffers.removeByOrderId("order-1").orElseThrow());
        assertTrue(activeOffers.findByOrderId("order-1").isEmpty());
        assertTrue(activeOffers.findByCourierId("courier-1").isEmpty());
        assertFalse(activeOffers.isCourierReserved("courier-1"));
    }

    @Test
    void clearRemovesAllOffersForReset() {
        activeOffers.add(offer("offer-1", "order-1", "courier-1"));
        activeOffers.add(offer("offer-2", "order-2", "courier-2"));

        activeOffers.clear();

        assertTrue(activeOffers.snapshot().isEmpty());
        assertFalse(activeOffers.isCourierReserved("courier-1"));
        assertFalse(activeOffers.isCourierReserved("courier-2"));
        assertTrue(activeOffers.reservedCourierIds().isEmpty());
    }

    @Test
    void rejectsMissingOfferAndLookupIds() {
        assertThrows(IllegalArgumentException.class, () -> activeOffers.add(null));
        assertThrows(IllegalArgumentException.class,
                () -> activeOffers.findByOrderId(" "));
        assertThrows(IllegalArgumentException.class,
                () -> activeOffers.findByCourierId(null));
        assertThrows(IllegalArgumentException.class,
                () -> activeOffers.removeByOrderId(" "));
    }

    private DispatchOffer offer(String offerId, String orderId, String courierId) {
        return new DispatchOffer(offerId, orderId, courierId, Instant.now());
    }
}
