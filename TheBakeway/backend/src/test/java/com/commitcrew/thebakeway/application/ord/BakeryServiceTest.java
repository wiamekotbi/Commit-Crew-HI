package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;

public class BakeryServiceTest {

    @Test
    void shouldReturnOpenBakeries() {
        // Arrange: replace the database repository with a mock we control.
        // The service is real, but this test starts neither Spring nor Supabase.
        BakeryRegistry bakeryRegistry = mock(BakeryRegistry.class);
        BakeryService bakeryService = new BakeryService(bakeryRegistry);

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
        BakeryService bakeryService = new BakeryService(bakeryRegistry);

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
        BakeryService bakeryService = new BakeryService(bakeryRegistry);

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
        BakeryService bakeryService = new BakeryService(bakeryRegistry);

        // Teach the mock to return an empty Optional for a non-existent bakery ID.
        when(bakeryRegistry.findById("missing-id")).thenReturn(java.util.Optional.empty());

        // Act & Assert: Expect an IllegalArgumentException when trying to fetch a non-existent bakery.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
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
        BakeryService bakeryService = new BakeryService(bakeryRegistry);

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
        BakeryService bakeryService = new BakeryService(bakeryRegistry);

        when(bakeryRegistry.findById("missing-id")).thenReturn(java.util.Optional.empty());

        // Act & Assert: Expect an IllegalArgumentException when trying to fetch the status of a non-existent bakery.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bakeryService.getBakeryStatus("missing-id");
        });

        // Assert that the exception message is as expected.
        assertEquals("Bakery not found with ID: missing-id", exception.getMessage());

        // Verify the service requested the bakery by ID.
        verify(bakeryRegistry).findById("missing-id");
    }
}
