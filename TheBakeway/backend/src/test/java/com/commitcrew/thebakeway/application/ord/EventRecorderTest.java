package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

class EventRecorderTest {

    @Test
    void recordsTheExactEventReceivedFromThePublisher() {
        EventRepository repository = mock(EventRepository.class);
        EventRecorder recorder = new EventRecorder(repository);
        StateTransitionEvent event = event();

        recorder.update(event);

        // Preserving the same instance keeps its event ID and timestamp intact.
        verify(repository).save(event);
    }

    @Test
    void rejectsMissingEventInsteadOfCallingTheRepository() {
        EventRepository repository = mock(EventRepository.class);
        EventRecorder recorder = new EventRecorder(repository);

        assertThrows(IllegalArgumentException.class, () -> recorder.update(null));
    }

    private StateTransitionEvent event() {
        return new StateTransitionEvent(
                "event-1",
                EntityType.BAKERY,
                "bakery-1",
                "open",
                "paused",
                Instant.parse("2026-10-18T18:00:00Z"));
    }
}
