package com.commitcrew.thebakeway.application.ord;

import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

/**
 * Observer contract from the updated UML. Event recorders and dashboard
 * read-model updaters implement this interface without being coupled to the
 * bakery, menu, or order service that produced the transition.
 */
public interface StateTransitionObserver {

    /** Reacts to one transition published by a state-changing service. */
    void update(StateTransitionEvent event);
}
