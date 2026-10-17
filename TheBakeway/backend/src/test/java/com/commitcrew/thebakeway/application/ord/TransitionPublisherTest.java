package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

class TransitionPublisherTest {

    @Test
    void notifiesEveryObserverWithSameEvent() {
        TransitionPublisher publisher = new TransitionPublisher();
        StateTransitionObserver first = mock(StateTransitionObserver.class);
        StateTransitionObserver second = mock(StateTransitionObserver.class);
        StateTransitionEvent event = event();
        publisher.attach(first);
        publisher.attach(second);

        publisher.notifyObservers(event);

        verify(first).update(event);
        verify(second).update(event);
    }

    @Test
    void ignoresDuplicateAttachment() {
        TransitionPublisher publisher = new TransitionPublisher();
        StateTransitionObserver observer = mock(StateTransitionObserver.class);
        StateTransitionEvent event = event();
        publisher.attach(observer);
        publisher.attach(observer);

        publisher.notifyObservers(event);

        // Duplicate registration must not deliver the same transition twice.
        verify(observer, times(1)).update(event);
    }

    @Test
    void detachedObserverIsNotNotified() {
        TransitionPublisher publisher = new TransitionPublisher();
        StateTransitionObserver observer = mock(StateTransitionObserver.class);
        StateTransitionEvent event = event();
        publisher.attach(observer);
        publisher.detach(observer);

        publisher.notifyObservers(event);

        verify(observer, never()).update(event);
    }

    @Test
    void propagatesObserverFailure() {
        TransitionPublisher publisher = new TransitionPublisher();
        StateTransitionObserver observer = mock(StateTransitionObserver.class);
        StateTransitionEvent event = event();
        RuntimeException failure = new RuntimeException("Recording failed");
        publisher.attach(observer);
        org.mockito.Mockito.doThrow(failure).when(observer).update(event);

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> publisher.notifyObservers(event));

        assertSame(failure, actual);
    }

    @Test
    void rejectsNullObserverAndEvent() {
        TransitionPublisher publisher = new TransitionPublisher();

        assertThrows(IllegalArgumentException.class, () -> publisher.attach(null));
        assertThrows(IllegalArgumentException.class, () -> publisher.detach(null));
        assertThrows(IllegalArgumentException.class, () -> publisher.notifyObservers(null));
    }

    private StateTransitionEvent event() {
        return new StateTransitionEvent(
                "event-1",
                EntityType.MENU_ITEM,
                "item-1",
                "available",
                "sold_out",
                Instant.parse("2026-10-17T18:00:00Z"));
    }
}
