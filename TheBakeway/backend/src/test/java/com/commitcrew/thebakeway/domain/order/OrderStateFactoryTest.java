package com.commitcrew.thebakeway.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OrderStateFactoryTest {

    @Test
    void rebuildsPlacedState() {
        OrderState state = OrderStateFactory.fromStatus("placed");

        assertInstanceOf(PlacedState.class, state);
        assertEquals("placed", state.getStatus());
    }

    @Test
    void rejectsUnknownStatus() {
        assertThrows(IllegalArgumentException.class, () -> OrderStateFactory.fromStatus("COMPLETED"));
        assertThrows(IllegalArgumentException.class, () -> OrderStateFactory.fromStatus(null));
    }
}
