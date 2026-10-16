package com.commitcrew.thebakeway.domain.order;

public class PreparingState implements OrderState {

    @Override
    public void markReady(Order order) {
        order.setState(new ReadyState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.preparing.name();
    }
}
