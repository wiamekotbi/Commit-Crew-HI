package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.application.dashboard.DashboardReadModel;
import com.commitcrew.thebakeway.application.dashboard.DashboardUpdater;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

class BakeryTransitionObserverIntegrationTest {

    @Test
    void successfulPauseReachesRecorderAndDashboard() {
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        EventRepository eventRepository = mock(EventRepository.class);
        DashboardReadModel dashboard = new DashboardReadModel();

        // Real Observer collaborators verify the complete publisher path used
        // in production while repository boundaries remain isolated mocks.
        TransitionPublisher publisher = new TransitionPublisher(List.of(
                new EventRecorder(eventRepository),
                new DashboardUpdater(dashboard)));
        BakeryService bakeryService = new BakeryService(bakeryRegistry, publisher);
        Bakery bakery = bakery(BakeryStatus.open);
        when(bakeryRegistry.findById("bakery-1")).thenReturn(Optional.of(bakery));
        when(bakeryRegistry.save(bakery)).thenReturn(bakery);

        bakeryService.pauseBakery("bakery-1");

        // The persisted history and dashboard projection describe the same
        // single transition produced by BakeryService.
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getEntityType() == EntityType.BAKERY
                        && event.getEntityId().equals("bakery-1")
                        && event.getPreviousState().equals("open")
                        && event.getNewState().equals("paused")));
        assertEquals("paused",
                dashboard.findState(EntityType.BAKERY, "bakery-1").orElseThrow());
    }

    @Test
    void unchangedPauseDoesNotReachAnyObserver() {
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        EventRepository eventRepository = mock(EventRepository.class);
        DashboardReadModel dashboard = new DashboardReadModel();
        TransitionPublisher publisher = new TransitionPublisher(List.of(
                new EventRecorder(eventRepository),
                new DashboardUpdater(dashboard)));
        BakeryService bakeryService = new BakeryService(bakeryRegistry, publisher);
        Bakery bakery = bakery(BakeryStatus.paused);
        when(bakeryRegistry.findById("bakery-1")).thenReturn(Optional.of(bakery));

        bakeryService.pauseBakery("bakery-1");

        verify(bakeryRegistry, never()).save(bakery);
        verify(eventRepository, never()).save(
                org.mockito.ArgumentMatchers.any(StateTransitionEvent.class));
        assertEquals(Optional.empty(),
                dashboard.findState(EntityType.BAKERY, "bakery-1"));
    }

    private Bakery bakery(BakeryStatus status) {
        return new Bakery("bakery-1", "Test Bakery", status, "123 Test Street");
    }
}
