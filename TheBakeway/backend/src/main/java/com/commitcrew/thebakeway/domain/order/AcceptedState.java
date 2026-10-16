package com.commitcrew.thebakeway.domain.order;

public class AcceptedState implements OrderState {

    @Override
    public void startPreparing(Order order) {
        order.setState(new PreparingState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.accepted.name();
    }
}
