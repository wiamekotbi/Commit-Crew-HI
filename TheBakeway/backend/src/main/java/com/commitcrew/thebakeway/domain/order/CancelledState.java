package com.commitcrew.thebakeway.domain.order;

// Final state: every action is refused
public class CancelledState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.cancelled.name();
    }
}
