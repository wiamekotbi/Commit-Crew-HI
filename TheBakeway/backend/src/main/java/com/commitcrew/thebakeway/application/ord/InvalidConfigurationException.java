package com.commitcrew.thebakeway.application.ord;

// The initial state config file is missing or has an invalid entry; nothing is loaded
public class InvalidConfigurationException extends IllegalArgumentException {

    public InvalidConfigurationException(String message) {
        super(message);
    }
}
