package com.commitcrew.thebakeway.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStateFactoryTest {

    @Test
    void rebuildsPlacedState() {
        OrderState state = OrderStateFactory.fromStatus("placed");

        assertInstanceOf(PlacedState.class, state);
        assertEquals("placed", state.getStatus());
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void rebuildsEveryStatus(OrderStatus status) {
        assertEquals(status.name(), OrderStateFactory.fromStatus(status.name()).getStatus());
    }

    @Test
    void rejectsUnknownStatus() {
        assertThrows(IllegalArgumentException.class, () -> OrderStateFactory.fromStatus("COMPLETED"));
        assertThrows(IllegalArgumentException.class, () -> OrderStateFactory.fromStatus(null));
    }
}
