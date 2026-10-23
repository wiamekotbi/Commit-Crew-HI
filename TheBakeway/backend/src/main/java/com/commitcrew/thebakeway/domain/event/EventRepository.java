package com.commitcrew.thebakeway.domain.event;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence boundary for the transition history shown in the Observer UML.
 * JpaRepository supplies save and lookup operations while services and
 * observers depend only on this event-specific contract.
 */
public interface EventRepository extends JpaRepository<StateTransitionEvent, String> {

    /** History of one entity's state changes, oldest first (UC-ORD-12, UC-ORD-13). */
    List<StateTransitionEvent> findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType entityType, String entityId);
}
