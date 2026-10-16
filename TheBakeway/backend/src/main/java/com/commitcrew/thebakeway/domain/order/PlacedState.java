package com.commitcrew.thebakeway.domain.order;

public class PlacedState implements OrderState {

    @Override
    public void accept(Order order) {
        order.setState(new AcceptedState());
    }

    @Override
    public String getStatus() {
        return OrderStatus.placed.name();
    }
}
