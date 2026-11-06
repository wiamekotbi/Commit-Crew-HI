package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;

@ExtendWith(MockitoExtension.class)
class CourierServiceTest {

    private static final String COURIER_ID = "courier-1";
    private static final String ORDER_ID = "order-1";
    private static final String OFFER_ID = "offer-1";

    @Mock
    private CourierRegistry courierRegistry;

    @Mock
    private OrderRegistry orderRegistry;

    @Mock
    private CourierSelectionService courierSelectionService;

    private ActiveDispatchOffers activeOffers;
    private DispatchWaitingQueue waitingQueue;
    private CourierService courierService;

    @BeforeEach
    void setUp() {
        activeOffers = new ActiveDispatchOffers();
        waitingQueue = new DispatchWaitingQueue();
        DispatchService dispatchService = new DispatchService(
                orderRegistry,
                courierRegistry,
                courierSelectionService,
                activeOffers,
                waitingQueue);
        courierService = new CourierService(courierRegistry, dispatchService);
    }

    @Test
    void goingOnDutyRetriesWaitingOrdersAndReturnsNewOffer() {
        Courier courier = new Courier(COURIER_ID);
        waitingQueue.enqueue(ORDER_ID);
        when(courierRegistry.findById(COURIER_ID))
                .thenReturn(Optional.of(courier));
        when(orderRegistry.findById(ORDER_ID))
                .thenReturn(Optional.of(readyOrder()));
        when(courierSelectionService.selectCourierForBakery(
                "bakery-1", Set.of()))
                .thenReturn(Optional.of(courier));

        CourierView view = courierService.goOnDuty(COURIER_ID);

        assertEquals(CourierStatus.IDLE, view.status());
        assertEquals(ORDER_ID, view.activeOffer().orderId());
        assertTrue(waitingQueue.snapshot().isEmpty());
        verify(courierRegistry).save(courier);
    }

    @Test
    void goingOffDutyDeclinesActiveOfferAndQueuesItsOrder() {
        Courier courier = idleCourier();
        activeOffer(ORDER_ID);
        when(courierRegistry.findById(COURIER_ID))
                .thenReturn(Optional.of(courier));

        CourierView view = courierService.goOffDuty(COURIER_ID);

        assertEquals(CourierStatus.OFFLINE, view.status());
        assertTrue(activeOffers.snapshot().isEmpty());
        assertEquals(java.util.List.of(ORDER_ID), waitingQueue.snapshot());
        verify(courierRegistry).save(courier);
    }

    @Test
    void currentCourierViewIncludesReservedOffer() {
        Courier courier = idleCourier();
        DispatchOffer offer = activeOffer(ORDER_ID);
        when(courierRegistry.findById(COURIER_ID))
                .thenReturn(Optional.of(courier));

        CourierView view = courierService.getCourier(COURIER_ID);

        assertEquals(CourierStatus.IDLE, view.status());
        assertEquals(offer, view.activeOffer());
    }

    @Test
    void acceptingOfferAssignsCourierAndClosesDispatchOffer() {
        Courier courier = idleCourier();
        DispatchOffer offer = activeOffer(ORDER_ID);
        when(courierRegistry.findById(COURIER_ID))
                .thenReturn(Optional.of(courier));

        DispatchOffer accepted = courierService.acceptOffer(
                COURIER_ID, OFFER_ID);

        assertEquals(offer, accepted);
        assertEquals(CourierStatus.ASSIGNED, courier.getStatus());
        assertEquals(ORDER_ID, courier.getCurrentOrderId());
        assertTrue(activeOffers.snapshot().isEmpty());
        assertTrue(waitingQueue.snapshot().isEmpty());
        verify(courierRegistry).save(courier);
    }

    @Test
    void decliningOfferKeepsCourierIdleAndQueuesOrderForRetry() {
        Courier courier = idleCourier();
        DispatchOffer offer = activeOffer(ORDER_ID);
        when(courierRegistry.findById(COURIER_ID))
                .thenReturn(Optional.of(courier));

        DispatchOffer declined = courierService.declineOffer(
                COURIER_ID, OFFER_ID);

        assertEquals(offer, declined);
        assertEquals(CourierStatus.IDLE, courier.getStatus());
        assertEquals(java.util.List.of(ORDER_ID), waitingQueue.snapshot());
        assertTrue(activeOffers.snapshot().isEmpty());
        verify(courierRegistry, never()).save(courier);
    }

    @Test
    void blocksSecondOrderAndLeavesOfferActive() {
        Courier courier = idleCourier();
        courier.acceptOffer("existing-order");
        DispatchOffer offer = activeOffer(ORDER_ID);
        when(courierRegistry.findById(COURIER_ID))
                .thenReturn(Optional.of(courier));

        assertThrows(IllegalStateException.class,
                () -> courierService.acceptOffer(COURIER_ID, OFFER_ID));

        assertEquals("existing-order", courier.getCurrentOrderId());
        assertEquals(offer,
                activeOffers.findByOfferId(OFFER_ID).orElseThrow());
        assertTrue(waitingQueue.snapshot().isEmpty());
        verify(courierRegistry, never()).save(courier);
    }

    @Test
    void refusesOfferBelongingToAnotherCourierBeforeChangingState() {
        activeOffers.add(new DispatchOffer(
                OFFER_ID, ORDER_ID, "courier-2", Instant.now()));

        assertThrows(SecurityException.class,
                () -> courierService.acceptOffer(COURIER_ID, OFFER_ID));

        verifyNoInteractions(courierRegistry);
        assertEquals(1, activeOffers.snapshot().size());
    }

    @Test
    void refusesUnknownCourierWithoutConsumingOffer() {
        DispatchOffer offer = activeOffer(ORDER_ID);
        when(courierRegistry.findById(COURIER_ID))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> courierService.declineOffer(COURIER_ID, OFFER_ID));

        assertEquals(offer,
                activeOffers.findByOfferId(OFFER_ID).orElseThrow());
        assertTrue(waitingQueue.snapshot().isEmpty());
    }

    private Courier idleCourier() {
        Courier courier = new Courier(COURIER_ID);
        courier.goOnDuty();
        return courier;
    }

    private DispatchOffer activeOffer(String orderId) {
        DispatchOffer offer = new DispatchOffer(
                OFFER_ID, orderId, COURIER_ID, Instant.now());
        activeOffers.add(offer);
        return offer;
    }

    private Order readyOrder() {
        Order order = new Order(
                "customer-1", "bakery-1", "Customer", "Montreal");
        order.accept();
        order.startPreparing();
        order.markReady();
        return order;
    }
}
