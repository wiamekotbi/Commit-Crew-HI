package com.commitcrew.thebakeway.domain.event;

import java.time.Instant;

/**
 * Immutable record of one domain object's state change. The same event instance
 * is delivered to every observer so they share its ID and timestamp.
 */
public final class StateTransitionEvent {

    // Identity and target fields let every observer refer to the same transition.
    private final String eventId;
    private final EntityType entityType;
    private final String entityId;

    // States remain strings because order, bakery, and menu-item enums are different types.
    private final String previousState;
    private final String newState;
    private final Instant timestamp;

    public StateTransitionEvent(
            String eventId,
            EntityType entityType,
            String entityId,
            String previousState,
            String newState,
            Instant timestamp) {
        requireNonBlank(eventId, "Event ID is required");
        if (entityType == null) {
            throw new IllegalArgumentException("Entity type is required");
        }
        requireNonBlank(entityId, "Entity ID is required");
        requireNonBlank(newState, "New state is required");
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        this.eventId = eventId;
        this.entityType = entityType;
        this.entityId = entityId;
        // Null is valid for creation events where no previous state exists.
        this.previousState = previousState;
        this.newState = newState;
        this.timestamp = timestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getPreviousState() {
        return previousState;
    }

    public String getNewState() {
        return newState;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    // Centralizes validation shared by the event ID, entity ID, and new state.
    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
