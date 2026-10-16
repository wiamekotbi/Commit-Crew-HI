package com.commitcrew.thebakeway.domain.order;

import java.util.Map;
import java.util.function.Supplier;

// Rebuilds the State object from the status stored in the database
public final class OrderStateFactory {

    private static final Map<String, Supplier<OrderState>> STATES = Map.of(
            OrderStatus.placed.name(), PlacedState::new);

    private OrderStateFactory() {
    }

    public static OrderState fromStatus(String status) {
        Supplier<OrderState> state = status == null ? null : STATES.get(status);
        if (state == null) {
            throw new IllegalArgumentException("Unknown order status: " + status);
        }
        return state.get();
    }
}
