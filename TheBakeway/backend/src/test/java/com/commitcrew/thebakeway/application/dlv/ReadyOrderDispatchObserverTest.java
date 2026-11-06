package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.commitcrew.thebakeway.application.ord.TransitionPublisher;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

class ReadyOrderDispatchObserverTest {

    private final DispatchService dispatchService = mock(DispatchService.class);
    private final ReadyOrderDispatchObserver observer =
            new ReadyOrderDispatchObserver(dispatchService);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publisherDispatchesReadyOrderWhenNoTransactionIsActive() {
        // This uses the real Observer subject, proving Spring's discovered
        // observer contract connects the ORDER event to dispatch.
        TransitionPublisher publisher = new TransitionPublisher(List.of(observer));

        publisher.notifyObservers(orderEvent("preparing", "ready"));

        verify(dispatchService).dispatchReadyOrder("order-1");
    }

    @Test
    void waitsForSuccessfulCommitBeforeDispatchingReadyOrder() {
        TransactionSynchronizationManager.initSynchronization();

        observer.update(orderEvent("preparing", "ready"));

        verify(dispatchService, never()).dispatchReadyOrder("order-1");

        // Simulate Spring's callback after the surrounding order transaction
        // commits successfully.
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(dispatchService).dispatchReadyOrder("order-1");
    }

    @Test
    void ignoresOtherOrderStatesAndOtherEntityTypes() {
        observer.update(orderEvent("accepted", "preparing"));
        observer.update(new StateTransitionEvent(
                "event-2",
                EntityType.BAKERY,
                "bakery-1",
                "paused",
                "ready",
                Instant.parse("2026-11-04T06:00:00Z")));

        verify(dispatchService, never()).dispatchReadyOrder(anyString());
    }

    @Test
    void rejectsNullTransitionEvent() {
        assertThrows(IllegalArgumentException.class, () -> observer.update(null));
    }

    private StateTransitionEvent orderEvent(String previousState, String newState) {
        return new StateTransitionEvent(
                "event-1",
                EntityType.ORDER,
                "order-1",
                previousState,
                newState,
                Instant.parse("2026-11-04T06:00:00Z"));
    }
}
