package com.commitcrew.thebakeway.application.dashboard;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

/**
 * Small read model containing the latest known state of each dashboard entity.
 * It is deliberately independent of bakery, menu, and order repositories so
 * the same transition event can update the dashboard consistently.
 */
@Component
public class DashboardReadModel {

    private final ConcurrentMap<EntityKey, String> currentStates = new ConcurrentHashMap<>();

    /** Applies one committed transition to the dashboard projection. */
    public void apply(StateTransitionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Transition event is required");
        }
        currentStates.put(new EntityKey(event.getEntityType(), event.getEntityId()),
                event.getNewState());
    }

    /** Allows dashboard queries without exposing the mutable backing map. */
    public Optional<String> findState(EntityType entityType, String entityId) {
        if (entityType == null || entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentStates.get(new EntityKey(entityType, entityId)));
    }

    /** Entity type is part of the key because IDs can overlap across domains. */
    private record EntityKey(EntityType entityType, String entityId) {
    }
}
