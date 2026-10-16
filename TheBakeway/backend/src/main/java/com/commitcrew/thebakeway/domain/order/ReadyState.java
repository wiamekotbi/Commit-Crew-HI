package com.commitcrew.thebakeway.domain.order;

import com.commitcrew.thebakeway.domain.account.Role;

public class ReadyState implements OrderState {

    @Override
    public void confirmPickup(Order order) {
        order.setState(new PickedUpState());
    }

    // Only a platform operator can cancel a ready order
    @Override
    public void cancel(Order order, Role role) {
        if (role != Role.OPERATOR) {
            throw new IllegalTransitionException(getStatus(), "cancel");
        }
        order.setState(new CancelledState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.ready.name();
    }
}
