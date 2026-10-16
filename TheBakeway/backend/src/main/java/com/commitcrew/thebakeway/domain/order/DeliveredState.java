package com.commitcrew.thebakeway.domain.order;

// Final state: every action is refused
public class DeliveredState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.delivered.name();
    }
}
