package com.commitcrew.thebakeway.domain.order;

import com.commitcrew.thebakeway.domain.account.Role;

public class AcceptedState implements OrderState {

    @Override
    public void startPreparing(Order order) {
        order.setState(new PreparingState());
    }

    // Only a platform operator can cancel once the order is accepted
    @Override
    public void cancel(Order order, Role role) {
        if (role != Role.OPERATOR) {
            throw new IllegalTransitionException(getStatus(), "cancel");
        }
        order.setState(new CancelledState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.accepted.name();
    }
}
