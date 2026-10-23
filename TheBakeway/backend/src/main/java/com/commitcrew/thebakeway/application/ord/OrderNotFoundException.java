package com.commitcrew.thebakeway.application.ord;

// The order does not exist, or belongs to another customer or bakery (reported the same way)
public class OrderNotFoundException extends IllegalArgumentException {

    public OrderNotFoundException() {
        super("Order not found");
    }
}
