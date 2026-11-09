package com.commitcrew.thebakeway.domain.order;

import java.util.Map;
import java.util.function.Supplier;

// Rebuilds the State object from the status stored in the database
public final class OrderStateFactory {

    private static final Map<String, Supplier<OrderState>> STATES = Map.of(
            OrderStatus.placed.name(), PlacedState::new,
            OrderStatus.accepted.name(), AcceptedState::new,
            OrderStatus.preparing.name(), PreparingState::new,
            OrderStatus.ready.name(), ReadyState::new,
            OrderStatus.picked_up.name(), PickedUpState::new,
            OrderStatus.delivered.name(), DeliveredState::new,
            OrderStatus.refunded.name(), RefundedState::new,
            OrderStatus.rejected.name(), RejectedState::new,
            OrderStatus.cancelled.name(), CancelledState::new);

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
