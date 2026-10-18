package com.commitcrew.thebakeway.application.ord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

@Service
public class BakeryService {
    private final BakeryRegistry bakeryRegistry;
    private final TransitionPublisher transitionPublisher;

    public BakeryService(
            BakeryRegistry bakeryRegistry,
            TransitionPublisher transitionPublisher) {
        this.bakeryRegistry = bakeryRegistry;
        this.transitionPublisher = transitionPublisher;
    }
    // 1. Fetch all bakeries with a specific status (e.g., open, paused, closed)
    public List<Bakery> getBakeriesByStatus(BakeryStatus status) {
        return bakeryRegistry.findByBakeryStatus(status);
    }

    // 2. Fetch a specific bakery by its ID
    public Bakery getBakeryById(String bakeryId) {
        return bakeryRegistry.findById(bakeryId)
                .orElseThrow(() -> new BakeryNotFoundException("Bakery not found with ID: " + bakeryId));
    }

    // 3. Fetch open bakeries
    public List<Bakery> getOpenBakeries() {
        return bakeryRegistry.findByBakeryStatus(BakeryStatus.open);
    }

    // 4. Fetch Status of a specific bakery by its ID
    public BakeryStatus getBakeryStatus(String bakeryId) {
        Bakery bakery = getBakeryById(bakeryId);
        return bakery.getBakeryStatus();
    }

    /** Pauses a bakery and emits one event only when its state actually changes. */
    @Transactional
    public Bakery pauseBakery(String bakeryId) {
        Bakery bakery = getBakeryById(bakeryId);
        BakeryStatus previousStatus = bakery.getBakeryStatus();

        if (!bakery.pause()) {
            // An unchanged state is a successful idempotent request, not a transition.
            return bakery;
        }

        return saveAndPublishTransition(bakery, previousStatus);
    }

    /** Reopens a paused bakery while preserving the permanent closed state. */
    @Transactional
    public Bakery reopenBakery(String bakeryId) {
        Bakery bakery = getBakeryById(bakeryId);
        BakeryStatus previousStatus = bakery.getBakeryStatus();

        if (!bakery.reopen()) {
            return bakery;
        }

        return saveAndPublishTransition(bakery, previousStatus);
    }

    /**
     * Persists before notifying observers, so a repository failure cannot
     * announce a transition that was never stored. The surrounding service
     * transaction also keeps synchronous observer work in the same operation.
     */
    private Bakery saveAndPublishTransition(
            Bakery bakery, BakeryStatus previousStatus) {
        Bakery savedBakery = bakeryRegistry.save(bakery);
        StateTransitionEvent event = new StateTransitionEvent(
                UUID.randomUUID().toString(),
                EntityType.BAKERY,
                savedBakery.getBakeryId(),
                previousStatus.name(),
                savedBakery.getBakeryStatus().name(),
                Instant.now());
        transitionPublisher.notifyObservers(event);
        return savedBakery;
    }



}
