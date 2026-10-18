package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Component;

import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

/**
 * Concrete Observer that stores every successfully published transition.
 * Spring discovers this component and supplies it to TransitionPublisher, so
 * state-changing services remain independent of the persistence mechanism.
 */
@Component
public class EventRecorder implements StateTransitionObserver {

    private final EventRepository eventRepository;

    public EventRecorder(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Uses the event's unique ID as the database primary key. A duplicate ID
     * therefore cannot silently create two records for one transition.
     */
    @Override
    public void update(StateTransitionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Transition event is required");
        }
        eventRepository.save(event);
    }
}
