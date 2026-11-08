package com.commitcrew.thebakeway.domain.order;

public class RefundedState implements OrderState {

    @Override
    public String getStatus() {
        return OrderStatus.refunded.name();
    }
}
