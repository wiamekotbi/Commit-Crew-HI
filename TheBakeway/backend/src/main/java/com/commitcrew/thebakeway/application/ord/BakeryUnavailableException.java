package com.commitcrew.thebakeway.application.ord;

public class BakeryUnavailableException extends IllegalStateException {
    public BakeryUnavailableException(String message) {
        super(message);
    }
    
}
