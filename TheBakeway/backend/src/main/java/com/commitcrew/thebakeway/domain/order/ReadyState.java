package com.commitcrew.thebakeway.domain.order;

public class ReadyState implements OrderState {

    @Override
    public void confirmPickup(Order order) {
        order.setState(new PickedUpState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.ready.name();
    }
}
