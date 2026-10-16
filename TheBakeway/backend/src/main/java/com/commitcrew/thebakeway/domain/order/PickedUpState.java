package com.commitcrew.thebakeway.domain.order;

public class PickedUpState implements OrderState {

    @Override
    public void confirmDelivery(Order order) {
        order.setState(new DeliveredState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.picked_up.name();
    }
}
