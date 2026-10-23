package com.commitcrew.thebakeway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.commitcrew.thebakeway.application.ord.InitialStateService;

// Loads the seeded accounts and the initial city when the application starts (UC-REG-03, UC-ORD-01).
// It runs once all beans exist but before the web server accepts requests, so nobody can log in
// or order while the data is being replaced.
// Turn it off with bakeway.load-initial-state=false (the context test does, to leave the shared database alone).
@Component
@ConditionalOnProperty(name = "bakeway.load-initial-state", havingValue = "true", matchIfMissing = true)
public class InitialStateStartup implements SmartInitializingSingleton {

    private static final Logger logger = LoggerFactory.getLogger(InitialStateStartup.class);

    private final InitialStateService initialStateService;

    public InitialStateStartup(InitialStateService initialStateService) {
        this.initialStateService = initialStateService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            initialStateService.loadInitialState();
        } catch (RuntimeException e) {
            // A bad config file must not stop the application; the previous state is kept
            logger.error("Initial state not loaded: {}", e.getMessage());
        }
    }
}
