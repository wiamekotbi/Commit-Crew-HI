package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;
import com.commitcrew.thebakeway.domain.location.Coordinates;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    private static final String ORDER_ID = "order-1";
    private static final String BAKERY_ID = "bakery-1";

    @Mock
    private OrderRegistry orderRegistry;

    @Mock
    private CourierSelectionService courierSelectionService;

    private ActiveDispatchOffers activeOffers;
    private DispatchWaitingQueue waitingQueue;
    private DispatchService service;

    @BeforeEach
    void setUp() {
        activeOffers = new ActiveDispatchOffers();
        waitingQueue = new DispatchWaitingQueue();
        service = new DispatchService(
                orderRegistry, courierSelectionService, activeOffers, waitingQueue);
    }

    @Test
    void createsOfferForReadyOrderAndSelectedCourier() {
        Order readyOrder = readyOrder();
        Courier courier = idleCourier("courier-1");
        waitingQueue.enqueue(ORDER_ID);
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(readyOrder));
        when(courierSelectionService.selectCourierForBakery(BAKERY_ID, Set.of()))
                .thenReturn(Optional.of(courier));

        DispatchOffer offer = service.dispatchReadyOrder(ORDER_ID).orElseThrow();

        assertEquals(ORDER_ID, offer.orderId());
        assertEquals("courier-1", offer.courierId());
        assertEquals(offer, activeOffers.findByOrderId(ORDER_ID).orElseThrow());
        assertTrue(waitingQueue.snapshot().isEmpty());
        // An offer is not yet an assignment; Wiame's acceptance command owns
        // the IDLE -> ASSIGNED transition.
        assertEquals(CourierStatus.IDLE, courier.getStatus());
    }

    @Test
    void queuesReadyOrderWhenNoCourierIsAvailable() {
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(readyOrder()));
        when(courierSelectionService.selectCourierForBakery(BAKERY_ID, Set.of()))
                .thenReturn(Optional.empty());

        assertTrue(service.dispatchReadyOrder(ORDER_ID).isEmpty());

        assertEquals(List.of(ORDER_ID), waitingQueue.snapshot());
        assertTrue(activeOffers.snapshot().isEmpty());
    }

    @Test
    void repeatedDispatchReturnsExistingOfferWithoutDuplicatingIt() {
        DispatchOffer existing = new DispatchOffer(
                "offer-1", ORDER_ID, "courier-1", Instant.now());
        activeOffers.add(existing);
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(readyOrder()));

        assertEquals(existing, service.dispatchReadyOrder(ORDER_ID).orElseThrow());

        assertEquals(1, activeOffers.snapshot().size());
        verifyNoInteractions(courierSelectionService);
    }

    @Test
    void excludesCourierReservedByAnotherOffer() {
        activeOffers.add(new DispatchOffer(
                "offer-existing", "order-existing", "courier-reserved", Instant.now()));
        Courier available = idleCourier("courier-available");
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(readyOrder()));
        when(courierSelectionService.selectCourierForBakery(
                BAKERY_ID, Set.of("courier-reserved")))
                .thenReturn(Optional.of(available));

        DispatchOffer offer = service.dispatchReadyOrder(ORDER_ID).orElseThrow();

        assertEquals("courier-available", offer.courierId());
        verify(courierSelectionService).selectCourierForBakery(
                BAKERY_ID, Set.of("courier-reserved"));
    }

    @Test
    void refusesToDispatchOrderThatIsNotReadyAndCleansStaleState() {
        Order placedOrder = new Order(
                "customer-1", BAKERY_ID, "Customer", "123 Main Street");
        waitingQueue.enqueue(ORDER_ID);
        activeOffers.add(new DispatchOffer(
                "offer-1", ORDER_ID, "courier-1", Instant.now()));
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(placedOrder));

        assertTrue(service.dispatchReadyOrder(ORDER_ID).isEmpty());

        assertTrue(waitingQueue.snapshot().isEmpty());
        assertTrue(activeOffers.findByOrderId(ORDER_ID).isEmpty());
        verifyNoInteractions(courierSelectionService);
    }

    @Test
    void rejectsMissingOrUnknownOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> service.dispatchReadyOrder(" "));
        verify(orderRegistry, never()).findById(" ");

        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class,
                () -> service.dispatchReadyOrder(ORDER_ID));
    }

    private Order readyOrder() {
        Order order = new Order(
                "customer-1", BAKERY_ID, "Customer", "123 Main Street");
        order.accept();
        order.startPreparing();
        order.markReady();
        return order;
    }

    private Courier idleCourier(String courierId) {
        Courier courier = new Courier(
                courierId, new Coordinates(45.5010, -73.5700));
        courier.goOnDuty();
        return courier;
    }
}
