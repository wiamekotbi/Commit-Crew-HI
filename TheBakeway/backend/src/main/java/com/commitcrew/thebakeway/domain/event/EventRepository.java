package com.commitcrew.thebakeway.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence boundary for the transition history shown in the Observer UML.
 * JpaRepository supplies save and lookup operations while services and
 * observers depend only on this event-specific contract.
 */
public interface EventRepository extends JpaRepository<StateTransitionEvent, String> {
}
