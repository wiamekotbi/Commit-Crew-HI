package com.commitcrew.thebakeway.domain.order;

import com.commitcrew.thebakeway.domain.account.Role;

public class PreparingState implements OrderState {

    @Override
    public void markReady(Order order) {
        order.setState(new ReadyState());
    }

    // Only a platform operator can cancel an order being prepared
    @Override
    public void cancel(Order order, Role role) {
        if (role != Role.OPERATOR) {
            throw new IllegalTransitionException(getStatus(), "cancel");
        }
        order.setState(new CancelledState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.preparing.name();
    }
}
