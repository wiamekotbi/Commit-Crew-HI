package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;

@ExtendWith(MockitoExtension.class)
class OperatorDispatchServiceTest {

    private static final String ORDER_ID = "order-1";
    private static final String BAKERY_ID = "bakery-1";
    private static final String PREVIOUS_COURIER_ID = "courier-old";

    @Mock
    private OrderRegistry orderRegistry;

    @Mock
    private CourierRegistry courierRegistry;

    @Mock
    private DispatchService dispatchService;

    private SessionManager sessionManager;
    private DispatchWaitingQueue waitingQueue;
    private OperatorDispatchService service;
    private String operatorSessionId;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        waitingQueue = new DispatchWaitingQueue();
        service = new OperatorDispatchService(
                sessionManager,
                new PermissionPolicy(),
                orderRegistry,
                courierRegistry,
                waitingQueue,
                dispatchService);
        operatorSessionId = sessionManager
                .createSession("operator-1", Role.OPERATOR)
                .getSessionId();
    }

    @Test
    void operatorSeesQueuedAndStalledOrdersWithoutCustomerDetails() {
        Order queuedOrder = readyOrderView("order-queued", "bakery-a");
        Order stalledOrder = readyOrderView("order-stalled", "bakery-b");
        Courier stalledCourier = mock(Courier.class);
        waitingQueue.enqueue("order-queued");

        when(orderRegistry.findById("order-queued"))
                .thenReturn(Optional.of(queuedOrder));
        when(orderRegistry.findById("order-stalled"))
                .thenReturn(Optional.of(stalledOrder));
        when(courierRegistry.findByStalledOrderIdIsNotNull())
                .thenReturn(List.of(stalledCourier));
        when(stalledCourier.getStalledOrderId()).thenReturn("order-stalled");
        when(stalledCourier.getCourierId()).thenReturn("courier-stalled");

        List<WaitingOrderView> result = service.getWaitingOrders(
                operatorSessionId);

        assertEquals(2, result.size());
        assertEquals(new WaitingOrderView(
                "order-queued", "bakery-a", "ready", 1, null, false),
                result.get(0));
        assertEquals(new WaitingOrderView(
                "order-stalled", "bakery-b", "ready", null,
                "courier-stalled", true), result.get(1));
        // WaitingOrderView intentionally has no customer, address, item, or
        // price component, so the endpoint cannot leak those order details.
        assertEquals(6, WaitingOrderView.class.getRecordComponents().length);
    }

    @Test
    void duplicateQueuedAndStalledOrderAppearsOnlyOnce() {
        Order order = readyOrderView(ORDER_ID, BAKERY_ID);
        Courier stalledCourier = mock(Courier.class);
        waitingQueue.enqueue(ORDER_ID);

        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(courierRegistry.findByStalledOrderIdIsNotNull())
                .thenReturn(List.of(stalledCourier));
        when(stalledCourier.getStalledOrderId()).thenReturn(ORDER_ID);
        when(stalledCourier.getCourierId()).thenReturn(PREVIOUS_COURIER_ID);

        List<WaitingOrderView> result = service.getWaitingOrders(
                operatorSessionId);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).queuePosition());
        assertEquals(PREVIOUS_COURIER_ID,
                result.get(0).stalledCourierId());
        assertTrue(result.get(0).reassignable());
    }

    @Test
    void nonOperatorCannotViewOrReassignDispatchWork() {
        String courierSession = sessionManager
                .createSession("courier-1", Role.COURIER)
                .getSessionId();

        assertThrows(SecurityException.class,
                () -> service.getWaitingOrders(courierSession));
        assertThrows(SecurityException.class,
                () -> service.reassignStalledOrder(courierSession, ORDER_ID));

        verifyNoInteractions(orderRegistry, courierRegistry, dispatchService);
    }

    @Test
    void successfulReassignmentReleasesOldCourierAndCreatesNewOffer() {
        Order order = readyOrderStatusOnly();
        Courier previousCourier = mock(Courier.class);
        DispatchOffer offer = new DispatchOffer(
                "offer-1", ORDER_ID, "courier-new", Instant.now());
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(courierRegistry.findByStalledOrderId(ORDER_ID))
                .thenReturn(Optional.of(previousCourier));
        when(previousCourier.getCourierId()).thenReturn(PREVIOUS_COURIER_ID);
        when(dispatchService.dispatchReadyOrder(ORDER_ID))
                .thenReturn(Optional.of(offer));

        DispatchReassignmentResult result = service.reassignStalledOrder(
                operatorSessionId, ORDER_ID);

        verify(previousCourier).releaseStalledOrder(ORDER_ID);
        verify(courierRegistry).save(previousCourier);
        verify(dispatchService).dispatchReadyOrder(ORDER_ID);
        assertEquals(ORDER_ID, result.orderId());
        assertEquals(PREVIOUS_COURIER_ID, result.previousCourierId());
        assertEquals("courier-new", result.offeredCourierId());
        assertFalse(result.waiting());
    }

    @Test
    void noReplacementLeavesResultWaitingAfterReleasingOldCourier() {
        Order order = readyOrderStatusOnly();
        Courier previousCourier = mock(Courier.class);
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(courierRegistry.findByStalledOrderId(ORDER_ID))
                .thenReturn(Optional.of(previousCourier));
        when(previousCourier.getCourierId()).thenReturn(PREVIOUS_COURIER_ID);
        when(dispatchService.dispatchReadyOrder(ORDER_ID))
                .thenReturn(Optional.empty());

        DispatchReassignmentResult result = service.reassignStalledOrder(
                operatorSessionId, ORDER_ID);

        verify(previousCourier).releaseStalledOrder(ORDER_ID);
        verify(courierRegistry).save(previousCourier);
        verify(dispatchService).dispatchReadyOrder(ORDER_ID);
        assertTrue(result.waiting());
        assertNull(result.offeredCourierId());
    }

    @Test
    void orderWithoutStalledCourierIsRejectedWithoutDispatching() {
        Order order = readyOrderStatusOnly();
        when(orderRegistry.findById(ORDER_ID))
                .thenReturn(Optional.of(order));
        when(courierRegistry.findByStalledOrderId(ORDER_ID))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.reassignStalledOrder(
                        operatorSessionId, ORDER_ID));

        verify(dispatchService, never()).dispatchReadyOrder(ORDER_ID);
    }

    @Test
    void nonReadyStalledOrderIsRejectedBeforeCourierMutation() {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn("picked_up");
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> service.reassignStalledOrder(
                        operatorSessionId, ORDER_ID));

        verify(courierRegistry, never()).findByStalledOrderId(ORDER_ID);
        verify(dispatchService, never()).dispatchReadyOrder(ORDER_ID);
    }

    @Test
    void missingSessionIsAuthenticationFailure() {
        assertThrows(IllegalStateException.class,
                () -> service.getWaitingOrders(null));
        assertThrows(IllegalStateException.class,
                () -> service.reassignStalledOrder(null, ORDER_ID));

        verifyNoInteractions(orderRegistry, courierRegistry, dispatchService);
    }

    private Order readyOrderView(String orderId, String bakeryId) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getBakeryId()).thenReturn(bakeryId);
        when(order.getStatus()).thenReturn("ready");
        return order;
    }

    private Order readyOrderStatusOnly() {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn("ready");
        return order;
    }
}
