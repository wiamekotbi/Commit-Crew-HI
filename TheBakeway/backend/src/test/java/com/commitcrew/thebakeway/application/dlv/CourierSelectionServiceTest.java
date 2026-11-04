package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.commitcrew.thebakeway.application.ord.BakeryNotFoundException;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;
import com.commitcrew.thebakeway.domain.location.Coordinates;

@ExtendWith(MockitoExtension.class)
class CourierSelectionServiceTest {

    private static final String BAKERY_ID = "bakery-1";
    private static final Coordinates BAKERY_LOCATION = new Coordinates(45.5000, -73.5700);

    @Mock
    private BakeryRegistry bakeryRegistry;

    @Mock
    private CourierRegistry courierRegistry;

    @Mock
    private CourierSelectionStrategy selectionStrategy;

    private CourierSelectionService service;

    @BeforeEach
    void setUp() {
        service = new CourierSelectionService(
                bakeryRegistry, courierRegistry, selectionStrategy);
    }

    @Test
    void suppliesBakeryLocationAndIdleCouriersToStrategy() {
        Bakery bakery = configuredBakery();
        Courier selectedCourier = idleCourier("courier-1");
        List<Courier> idleCouriers = List.of(selectedCourier);

        when(bakeryRegistry.findById(BAKERY_ID)).thenReturn(Optional.of(bakery));
        when(courierRegistry.findByStatus(CourierStatus.IDLE)).thenReturn(idleCouriers);
        when(selectionStrategy.selectCourier(BAKERY_LOCATION, idleCouriers))
                .thenReturn(Optional.of(selectedCourier));

        Optional<Courier> result = service.selectCourierForBakery(BAKERY_ID);

        assertEquals(selectedCourier, result.orElseThrow());
        // Selection is read-only. Assignment remains an explicit later step.
        assertEquals(CourierStatus.IDLE, selectedCourier.getStatus());
        assertNull(selectedCourier.getCurrentOrderId());
        verify(selectionStrategy).selectCourier(BAKERY_LOCATION, idleCouriers);
    }

    @Test
    void returnsEmptyWhenStrategyFindsNoEligibleCourier() {
        Bakery bakery = configuredBakery();
        List<Courier> noIdleCouriers = List.of();

        when(bakeryRegistry.findById(BAKERY_ID)).thenReturn(Optional.of(bakery));
        when(courierRegistry.findByStatus(CourierStatus.IDLE)).thenReturn(noIdleCouriers);
        when(selectionStrategy.selectCourier(BAKERY_LOCATION, noIdleCouriers))
                .thenReturn(Optional.empty());

        assertTrue(service.selectCourierForBakery(BAKERY_ID).isEmpty());
    }

    @Test
    void rejectsMissingBakeryWithoutQueryingCouriers() {
        when(bakeryRegistry.findById(BAKERY_ID)).thenReturn(Optional.empty());

        assertThrows(BakeryNotFoundException.class,
                () -> service.selectCourierForBakery(BAKERY_ID));

        verifyNoInteractions(courierRegistry, selectionStrategy);
    }

    @Test
    void rejectsBakeryWithoutCoordinatesBeforeSelection() {
        Bakery bakeryWithoutLocation = new Bakery(
                BAKERY_ID, "Legacy Bakery", BakeryStatus.open, "123 Main Street");
        when(bakeryRegistry.findById(BAKERY_ID))
                .thenReturn(Optional.of(bakeryWithoutLocation));

        assertThrows(IllegalStateException.class,
                () -> service.selectCourierForBakery(BAKERY_ID));

        verify(courierRegistry, never()).findByStatus(CourierStatus.IDLE);
        verifyNoInteractions(selectionStrategy);
    }

    @Test
    void rejectsBlankBakeryIdBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.selectCourierForBakery("  "));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectCourierForBakery(null));

        verifyNoInteractions(bakeryRegistry, courierRegistry, selectionStrategy);
    }

    private Bakery configuredBakery() {
        return new Bakery(
                BAKERY_ID,
                "Sweet Treats",
                BakeryStatus.open,
                "123 Main Street",
                BAKERY_LOCATION);
    }

    private Courier idleCourier(String courierId) {
        Courier courier = new Courier(
                courierId, new Coordinates(45.5010, -73.5700));
        courier.goOnDuty();
        return courier;
    }
}
