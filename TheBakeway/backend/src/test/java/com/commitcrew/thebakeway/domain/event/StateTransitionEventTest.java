package com.commitcrew.thebakeway.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class StateTransitionEventTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-10-17T18:00:00Z");

    @Test
    void preservesTransitionDetails() {
        StateTransitionEvent event = event("event-1", EntityType.BAKERY, "bakery-1",
                "open", "paused", TIMESTAMP);

        assertEquals("event-1", event.getEventId());
        assertEquals(EntityType.BAKERY, event.getEntityType());
        assertEquals("bakery-1", event.getEntityId());
        assertEquals("open", event.getPreviousState());
        assertEquals("paused", event.getNewState());
        assertEquals(TIMESTAMP, event.getTimestamp());
    }

    @Test
    void allowsMissingPreviousStateForCreationEvent() {
        StateTransitionEvent event = event("event-2", EntityType.ORDER, "order-1",
                null, "placed", TIMESTAMP);

        assertNull(event.getPreviousState());
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThrows(IllegalArgumentException.class,
                () -> event(null, EntityType.BAKERY, "bakery-1", "open", "paused", TIMESTAMP));
        assertThrows(IllegalArgumentException.class,
                () -> event("   ", EntityType.BAKERY, "bakery-1", "open", "paused", TIMESTAMP));
        assertThrows(IllegalArgumentException.class,
                () -> event("event-1", null, "bakery-1", "open", "paused", TIMESTAMP));
        assertThrows(IllegalArgumentException.class,
                () -> event("event-1", EntityType.BAKERY, "", "open", "paused", TIMESTAMP));
        assertThrows(IllegalArgumentException.class,
                () -> event("event-1", EntityType.BAKERY, "bakery-1", "open", " ", TIMESTAMP));
        assertThrows(IllegalArgumentException.class,
                () -> event("event-1", EntityType.BAKERY, "bakery-1", "open", "paused", null));
    }

    private StateTransitionEvent event(
            String eventId,
            EntityType entityType,
            String entityId,
            String previousState,
            String newState,
            Instant timestamp) {
        return new StateTransitionEvent(eventId, entityType, entityId,
                previousState, newState, timestamp);
    }
}
