package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

public class BakeryServiceTest {

    @Test
    void shouldReturnOpenBakeries() {
        // Arrange: replace the database repository with a mock we control.
        // The service is real, but this test starts neither Spring nor Supabase.
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, mock(TransitionPublisher.class));

        Bakery bakery = new Bakery("bakery-123", "Sweet Treats", BakeryStatus.open, "123 Main St");
        List<Bakery> expectedBakeries = List.of(bakery);

        // Teach the mock what to return when asked specifically for open bakeries.
        when(bakeryRegistry.findByBakeryStatus(BakeryStatus.open)).thenReturn(expectedBakeries);

        // Act: exercise the public service method used for open-bakery retrieval.
        List<Bakery> actualBakeries = bakeryService.getOpenBakeries();

        // Assert: the service returns the expected bakery records.
        assertEquals(expectedBakeries, actualBakeries);

        // Verify the service requested the open status. This checks service behavior,
        // not whether the real database correctly executes the repository query.
        verify(bakeryRegistry).findByBakeryStatus(BakeryStatus.open);
    }

    @Test 
    void shouldReturnEmptyListWhenNoBakeriesAreOpen(){
        // Arrange
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, mock(TransitionPublisher.class));

        // Teach the mock to return an empty list for open bakeries.
        when(bakeryRegistry.findByBakeryStatus(BakeryStatus.open)).thenReturn(List.of());

        // Act
        List<Bakery> actualBakeries = bakeryService.getOpenBakeries();

        // Assert
        assertEquals(List.of(), actualBakeries);

        // Verify the service requested the open status.
        verify(bakeryRegistry).findByBakeryStatus(BakeryStatus.open);

    }

    @Test
    void shouldReturnBakeryById(){
        // Arrange
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, mock(TransitionPublisher.class));

        Bakery bakery = new Bakery("bakery-123", "Sweet Treats", BakeryStatus.open, "123 Main St");

        // Teach the mock to return the specific bakery when queried by ID.
        when(bakeryRegistry.findById("bakery-123")).thenReturn(java.util.Optional.of(bakery));

        // Act
        Bakery actualBakery = bakeryService.getBakeryById("bakery-123");

        // Assert
        assertEquals(bakery, actualBakery);

        // Verify the service requested the bakery by ID.
        verify(bakeryRegistry).findById("bakery-123");
    }

    @Test 
    void shouldThrowExceptionWhenBakeryDoesNotExist(){
        // Arrange
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, mock(TransitionPublisher.class));

        // Teach the mock to return an empty Optional for a non-existent bakery ID.
        when(bakeryRegistry.findById("missing-id")).thenReturn(java.util.Optional.empty());

        // Act & Assert: a missing bakery must produce the specific BakeryNotFoundException.
        BakeryNotFoundException exception = assertThrows(BakeryNotFoundException.class, () -> {
            bakeryService.getBakeryById("missing-id");
        });

        // Assert that the exception message is as expected.
        assertEquals("Bakery not found with ID: missing-id", exception.getMessage());

        // Verify the service requested the bakery by ID.
        verify(bakeryRegistry).findById("missing-id");
    }

    @Test 
    void shouldReturnPausedBakeryStatus(){
        // Arrange
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, mock(TransitionPublisher.class));

        Bakery bakery = new Bakery("bakery-123", "Sweet Treats", BakeryStatus.paused, "123 Main St");

        // Teach the mock to return the specific bakery when queried by ID.
        when(bakeryRegistry.findById("bakery-123")).thenReturn(java.util.Optional.of(bakery));

        // Act
        BakeryStatus actualStatus = bakeryService.getBakeryStatus("bakery-123");

        // Assert
        assertEquals(BakeryStatus.paused, actualStatus);

        // Verify the service requested the bakery by ID.
        verify(bakeryRegistry).findById("bakery-123");
    }

    @Test 
    void shouldThrowExceptionWhenGettingStatusOfMissingBakery(){
        // Arrange
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, mock(TransitionPublisher.class));

        when(bakeryRegistry.findById("missing-id")).thenReturn(java.util.Optional.empty());

        // Act & Assert: status lookup must preserve the BakeryNotFoundException from bakery lookup.
        BakeryNotFoundException exception = assertThrows(BakeryNotFoundException.class, () -> {
            bakeryService.getBakeryStatus("missing-id");
        });

        // Assert that the exception message is as expected.
        assertEquals("Bakery not found with ID: missing-id", exception.getMessage());

        // Verify the service requested the bakery by ID.
        verify(bakeryRegistry).findById("missing-id");
    }

    @Test
    void shouldPersistPauseAndPublishBakeryTransition() {
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        TransitionPublisher transitionPublisher = mock(TransitionPublisher.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, transitionPublisher);
        Bakery bakery = new Bakery(
                "bakery-123", "Sweet Treats", BakeryStatus.open, "123 Main St");
        when(bakeryRegistry.findById("bakery-123")).thenReturn(Optional.of(bakery));
        when(bakeryRegistry.save(bakery)).thenReturn(bakery);

        Bakery updated = bakeryService.pauseBakery("bakery-123");

        assertEquals(BakeryStatus.paused, updated.getBakeryStatus());
        verify(bakeryRegistry).save(bakery);

        // Capture generated event data while leaving its UUID and timestamp unconstrained.
        ArgumentCaptor<StateTransitionEvent> eventCaptor =
                ArgumentCaptor.forClass(StateTransitionEvent.class);
        verify(transitionPublisher).notifyObservers(eventCaptor.capture());
        StateTransitionEvent event = eventCaptor.getValue();
        assertEquals(EntityType.BAKERY, event.getEntityType());
        assertEquals("bakery-123", event.getEntityId());
        assertEquals("open", event.getPreviousState());
        assertEquals("paused", event.getNewState());
    }

    @Test
    void shouldPersistReopenAndPublishBakeryTransition() {
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        TransitionPublisher transitionPublisher = mock(TransitionPublisher.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, transitionPublisher);
        Bakery bakery = new Bakery(
                "bakery-123", "Sweet Treats", BakeryStatus.paused, "123 Main St");
        when(bakeryRegistry.findById("bakery-123")).thenReturn(Optional.of(bakery));
        when(bakeryRegistry.save(bakery)).thenReturn(bakery);

        Bakery updated = bakeryService.reopenBakery("bakery-123");

        assertEquals(BakeryStatus.open, updated.getBakeryStatus());
        verify(bakeryRegistry).save(bakery);
        ArgumentCaptor<StateTransitionEvent> eventCaptor =
                ArgumentCaptor.forClass(StateTransitionEvent.class);
        verify(transitionPublisher).notifyObservers(eventCaptor.capture());
        assertEquals("paused", eventCaptor.getValue().getPreviousState());
        assertEquals("open", eventCaptor.getValue().getNewState());
    }

    @Test
    void shouldNotPersistOrPublishUnchangedBakeryStatus() {
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        TransitionPublisher transitionPublisher = mock(TransitionPublisher.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, transitionPublisher);
        Bakery alreadyPaused = new Bakery(
                "bakery-123", "Sweet Treats", BakeryStatus.paused, "123 Main St");
        when(bakeryRegistry.findById("bakery-123")).thenReturn(Optional.of(alreadyPaused));

        Bakery unchanged = bakeryService.pauseBakery("bakery-123");

        assertEquals(BakeryStatus.paused, unchanged.getBakeryStatus());
        verify(bakeryRegistry, never()).save(alreadyPaused);
        verifyNoInteractions(transitionPublisher);
    }

    @Test
    void shouldAllowNewOrderForOpenBakery() {
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        TransitionPublisher transitionPublisher = mock(TransitionPublisher.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, transitionPublisher);
        Bakery openBakery = new Bakery(
                "bakery-123", "Sweet Treats", BakeryStatus.open, "123 Main St");
        when(bakeryRegistry.findById("bakery-123")).thenReturn(Optional.of(openBakery));

        Bakery accepted = bakeryService.requireOpenForOrdering("bakery-123");

        assertEquals(openBakery, accepted);
        // This guard only reads current status. It must not persist anything or
        // publish a transition while validating a customer's new order.
        verify(bakeryRegistry, never()).save(openBakery);
        verifyNoInteractions(transitionPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = BakeryStatus.class, names = {"paused", "closed"})
    void shouldRejectNewOrderWhenBakeryIsNotOpen(BakeryStatus unavailableStatus) {
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        TransitionPublisher transitionPublisher = mock(TransitionPublisher.class);
        BakeryService bakeryService = new BakeryService(
                bakeryRegistry, transitionPublisher);
        Bakery unavailableBakery = new Bakery(
                "bakery-123", "Sweet Treats", unavailableStatus, "123 Main St");
        when(bakeryRegistry.findById("bakery-123"))
                .thenReturn(Optional.of(unavailableBakery));

        BakeryUnavailableException exception = assertThrows(
                BakeryUnavailableException.class,
                () -> bakeryService.requireOpenForOrdering("bakery-123"));

        assertEquals("Bakery bakery-123 is unavailable for ordering: "
                + unavailableStatus, exception.getMessage());
        // Rejection is read-only and therefore cannot disturb existing orders,
        // preparation queues, bakery state, or transition observers.
        verify(bakeryRegistry, never()).save(unavailableBakery);
        verifyNoInteractions(transitionPublisher);
    }
}
