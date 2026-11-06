package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DispatchOfferTest {

    @Test
    void keepsValidatedOfferDetails() {
        Instant offeredAt = Instant.parse("2026-11-04T05:00:00Z");

        DispatchOffer offer = new DispatchOffer(
                "offer-1", "order-1", "courier-1", offeredAt);

        assertEquals("offer-1", offer.offerId());
        assertEquals("order-1", offer.orderId());
        assertEquals("courier-1", offer.courierId());
        assertEquals(offeredAt, offer.offeredAt());
    }

    @Test
    void rejectsMissingOfferDetails() {
        Instant now = Instant.now();

        assertThrows(IllegalArgumentException.class,
                () -> new DispatchOffer(null, "order-1", "courier-1", now));
        assertThrows(IllegalArgumentException.class,
                () -> new DispatchOffer("offer-1", " ", "courier-1", now));
        assertThrows(IllegalArgumentException.class,
                () -> new DispatchOffer("offer-1", "order-1", " ", now));
        assertThrows(IllegalArgumentException.class,
                () -> new DispatchOffer("offer-1", "order-1", "courier-1", null));
    }
}
