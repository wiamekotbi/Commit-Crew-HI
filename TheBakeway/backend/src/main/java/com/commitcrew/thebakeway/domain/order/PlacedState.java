package com.commitcrew.thebakeway.domain.order;

import com.commitcrew.thebakeway.domain.account.Role;

public class PlacedState implements OrderState {

    @Override
    public void accept(Order order) {
        order.setState(new AcceptedState());
    }

    @Override
    public void reject(Order order, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        order.setRejectionReason(reason);
        order.setState(new RejectedState());
    }

    // A customer can cancel only while the order is placed; an operator can always cancel here
    @Override
    public void cancel(Order order, Role role) {
        if (role != Role.CUSTOMER && role != Role.OPERATOR) {
            throw new IllegalTransitionException(getStatus(), "cancel");
        }
        order.setState(new CancelledState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.placed.name();
    }
}
