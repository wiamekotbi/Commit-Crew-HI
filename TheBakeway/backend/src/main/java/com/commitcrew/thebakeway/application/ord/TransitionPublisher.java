package com.commitcrew.thebakeway.application.ord;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

/**
 * Subject in the Observer pattern shown in the updated UML. It synchronously
 * publishes a transition to each registered StateTransitionObserver.
 * Synchronous delivery allows observer failures to propagate to the calling
 * service, where a later transactional integration can roll back the operation.
 */
@Component
public class TransitionPublisher {

    // The UML specifies a List<StateTransitionObserver>. Registration order is
    // preserved, while attach prevents the same observer from appearing twice.
    private final List<StateTransitionObserver> observers = new ArrayList<>();

    /** Registers an observer once; attaching the same instance again is harmless. */
    public void attach(StateTransitionObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("Observer is required");
        }
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /** Removes an observer. Removing an observer that is not registered is harmless. */
    public void detach(StateTransitionObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("Observer is required");
        }
        observers.remove(observer);
    }

    /** Delivers the same immutable event to every currently registered observer. */
    public void notifyObservers(StateTransitionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Transition event is required");
        }
        for (StateTransitionObserver observer : observers) {
            observer.update(event);
        }
    }
}
