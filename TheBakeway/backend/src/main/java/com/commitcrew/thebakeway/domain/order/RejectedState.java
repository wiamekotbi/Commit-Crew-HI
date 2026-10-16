package com.commitcrew.thebakeway.domain.order;

// Final state: every action is refused
public class RejectedState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.rejected.name();
    }
}
