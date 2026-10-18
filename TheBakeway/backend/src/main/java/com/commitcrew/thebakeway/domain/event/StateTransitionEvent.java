package com.commitcrew.thebakeway.domain.event;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Immutable record of one domain object's state change. The same event instance
 * is delivered to every observer so they share its ID and timestamp.
 */
@Entity
@Table(name = "state_transition_events")
public class StateTransitionEvent {

    // Identity and target fields let every observer refer to the same transition.
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private String entityId;

    // States remain strings because order, bakery, and menu-item enums are different types.
    @Column(name = "previous_state", updatable = false)
    private String previousState;

    @Column(name = "new_state", nullable = false, updatable = false)
    private String newState;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant timestamp;

    /**
     * Required only by JPA when rebuilding an event from the database. Keeping
     * it protected and exposing no setters preserves immutability for callers.
     */
    protected StateTransitionEvent() {
    }

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
