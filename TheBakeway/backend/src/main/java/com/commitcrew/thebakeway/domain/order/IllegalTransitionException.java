package com.commitcrew.thebakeway.domain.order;

// Extends IllegalArgumentException so controllers map it to the same 400 error response
public class IllegalTransitionException extends IllegalArgumentException {

    public IllegalTransitionException(String currentStatus, String action) {
        super("Cannot " + action + " an order that is " + currentStatus);
    }
}
