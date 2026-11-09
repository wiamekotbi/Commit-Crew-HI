package com.commitcrew.thebakeway.domain.order;

import com.commitcrew.thebakeway.domain.account.Role;

// State pattern: every action is refused by default; each state overrides only its legal actions.
// States never touch the preparation queue or publish events (OrderServiceImpl does that).
public interface OrderState {

    default void accept(Order order) {
        throw new IllegalTransitionException(getStatus(), "accept");
    }

    default void reject(Order order, String reason) {
        throw new IllegalTransitionException(getStatus(), "reject");
    }

    default void startPreparing(Order order) {
        throw new IllegalTransitionException(getStatus(), "start preparing");
    }

    default void markReady(Order order) {
        throw new IllegalTransitionException(getStatus(), "mark ready");
    }

    default void confirmPickup(Order order) {
        throw new IllegalTransitionException(getStatus(), "confirm pickup of");
    }

    default void confirmDelivery(Order order) {
        throw new IllegalTransitionException(getStatus(), "confirm delivery of");
    }

    default void refund(Order order) {
        throw new IllegalTransitionException(getStatus(), "refund");
    }

    default void cancel(Order order, Role role) {
        throw new IllegalTransitionException(getStatus(), "cancel");
    }

    String getStatus();
}
