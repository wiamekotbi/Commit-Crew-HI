package com.commitcrew.thebakeway.domain.order;

public class PlacedState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.placed.name();
    }
}
