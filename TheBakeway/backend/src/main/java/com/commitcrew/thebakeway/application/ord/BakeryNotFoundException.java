package com.commitcrew.thebakeway.application.ord;

public class BakeryNotFoundException extends IllegalArgumentException {
    public BakeryNotFoundException(String message) {
        super(message);
    }
}
