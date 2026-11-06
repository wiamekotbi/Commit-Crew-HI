package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.dlv.DispatchService;
import com.commitcrew.thebakeway.application.ord.*;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

class DeliveryPricingIntegrationTest {

    private final OrderRegistry orders = mock(OrderRegistry.class);
    private final CourierRegistry couriers = mock(CourierRegistry.class);
    private final PricingService pricing = mock(PricingService.class);
    private final PricingBilling billing = mock(PricingBilling.class);
    private final DispatchService dispatch = mock(DispatchService.class);
    private final SessionManager sessions = new SessionManager();
    private final CommandConsole console = new CommandConsole();

    @Test
    void pickupDoesNotPriceAndDeliveryPricesOnceWithoutChangingDispatch() {
        Order order = readyOrder();
        Courier courier = assignedCourier();
        PriceBreakdown price = new PriceBreakdown(8, 3, 1, 2, 14);
        when(pricing.calculatePrice(order)).thenReturn(price);
        when(billing.recordChargeIfAbsent(eq(order), eq(price), any())).thenReturn(true);
        OrderService service = service(Optional.of(billing));
        String session = sessions.createSession("courier-1", Role.COURIER).getSessionId();

        service.confirmPickup(session, "order-1");

        assertEquals("picked_up", order.getStatus());
        assertEquals(CourierStatus.CARRYING, courier.getStatus());
        verifyNoInteractions(pricing, billing);

        service.confirmDelivery(session, "order-1");

        assertEquals("delivered", order.getStatus());
        assertEquals(CourierStatus.IDLE, courier.getStatus());
        assertNull(courier.getCurrentOrderId());
        verify(pricing, times(1)).calculatePrice(order);
        verify(billing, times(1)).recordChargeIfAbsent(eq(order), eq(price), any());
        verify(dispatch).retryWaitingOrdersWhenCourierBecomesIdle("courier-1");

        assertThrows(IllegalStateException.class, () -> service.confirmDelivery(session, "order-1"));

        verify(pricing, times(1)).calculatePrice(order);
        verify(billing, times(1)).recordChargeIfAbsent(eq(order), eq(price), any());
        verify(dispatch, times(1)).retryWaitingOrdersWhenCourierBecomesIdle("courier-1");
    }

    @Test
    void missingBillingKeepsExistingDeliveryWorkingAndReportsMissingHandoff() {
        Order order = readyOrder();
        assignedCourier();
        when(pricing.calculatePrice(order)).thenReturn(new PriceBreakdown(8, 3, 1, 2, 14));
        OrderService service = service(Optional.empty());
        String session = sessions.createSession("courier-1", Role.COURIER).getSessionId();
        service.confirmPickup(session, "order-1");

        service.confirmDelivery(session, "order-1");

        assertEquals("delivered", order.getStatus());
        verifyNoInteractions(billing);
        assertFalse(console.entriesAfter(0).get(0).success());
        assertTrue(console.entriesAfter(0).get(0).detail().contains("billing integration is not connected"));
    }

    @Test
    void customerCannotTriggerDeliveryOrPricing() {
        OrderService service = service(Optional.of(billing));
        String session = sessions.createSession("customer-1", Role.CUSTOMER).getSessionId();

        assertThrows(SecurityException.class, () -> service.confirmDelivery(session, "order-1"));

        verifyNoInteractions(orders, couriers, pricing, billing, dispatch);
    }

    private OrderService service(Optional<PricingBilling> billingIntegration) {
        DeliveryPricingObserver observer = new DeliveryPricingObserver(orders, pricing, billingIntegration, console);
        TransitionPublisher publisher = new TransitionPublisher(List.of(observer));
        OrderServiceImpl implementation = new OrderServiceImpl(
                mock(BakeryService.class), mock(MenuService.class), mock(BakeryStaffAccessService.class),
                mock(BakeryStatusAccessService.class), orders, mock(AccountRegistry.class), sessions,
                mock(PreparationQueue.class), publisher, mock(InitialStateService.class),
                mock(EventRepository.class), couriers, dispatch);
        return new OrderServiceProxy(implementation, sessions, new PermissionPolicy());
    }

    private Order readyOrder() {
        Order order = new Order("customer-1", "bakery-1", "Maria", "Montreal");
        ReflectionTestUtils.setField(order, "id", "order-1");
        order.addOrderItem(new MenuItem("bakery-1", "Croissant", "", 4, MenuItemStatus.available), 2);
        order.accept();
        order.startPreparing();
        order.markReady();
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        when(orders.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        return order;
    }

    private Courier assignedCourier() {
        Courier courier = new Courier("courier-1");
        courier.goOnDuty();
        courier.assignOrder("order-1");
        when(couriers.findById("courier-1")).thenReturn(Optional.of(courier));
        return courier;
    }
}
