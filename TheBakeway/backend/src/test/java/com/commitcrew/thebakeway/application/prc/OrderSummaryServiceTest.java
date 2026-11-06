package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;
import com.commitcrew.thebakeway.domain.session.Session;

@ExtendWith(MockitoExtension.class)
class OrderSummaryServiceTest {

    private static final Instant DELIVERED_AT = Instant.parse("2026-11-02T15:00:00Z");

    @Mock
    private SessionManager sessionManager;
    @Mock
    private OrderRegistry orderRegistry;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrderSummaryBilling billing;

    private OrderSummaryService service;

    @BeforeEach
    void setUp() {
        service = new OrderSummaryService(sessionManager, orderRegistry, eventRepository, Optional.of(billing));
    }

    @ParameterizedTest
    @ValueSource(strings = {"delivered", "refunded"})
    void returnsOriginalItemsAndDeliveryTimestampForDeliveredAndRefundedOrders(String status) {
        customerSession();
        MenuItem item = new MenuItem("bakery-1", "Original croissant", "", 4.5, MenuItemStatus.available);
        Order order = spy(new Order("customer-1", "bakery-1", "Maria", "Montreal"));
        ReflectionTestUtils.setField(order, "id", "order-1");
        order.addOrderItem(item, 2);
        order.accept();
        order.startPreparing();
        order.markReady();
        order.confirmPickup();
        order.confirmDelivery();
        when(order.getStatus()).thenReturn(status);
        item.updateDetails("New croissant", "", 100);
        when(orderRegistry.findById("order-1")).thenReturn(Optional.of(order));
        PriceBreakdown saved = new PriceBreakdown(9, 3, 1, 2, 15);
        chargeAndDelivery(saved);

        OrderSummary result = service.getSummary("session-1", "order-1");

        assertEquals("order-1", result.orderId());
        assertEquals(status, result.status());
        assertEquals(List.of(new OrderSummary.Item("Original croissant", 2, 4.5, 9)), result.items());
        assertEquals("charge-1", result.chargeId());
        assertSame(saved, result.pricing());
        assertEquals(DELIVERED_AT, result.deliveredAt());
        assertEquals(List.of(), result.refunds());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 2.5})
    void returnsRecordedBreakdownWithOrWithoutSurcharge(double surcharge) {
        customerSession();
        eligibleOrder("delivered");
        PriceBreakdown saved = new PriceBreakdown(12, 4, 1.5, surcharge, 17.5 + surcharge);
        chargeAndDelivery(saved);

        assertEquals(saved, service.getSummary("session-1", "order-1").pricing());
    }

    @Test
    void preservesOriginalChargeAfterRefundAndUsesDeliveryEventRatherThanRefundTime() {
        customerSession();
        eligibleOrder("refunded");
        PriceBreakdown saved = new PriceBreakdown(12, 4, 1.5, 0, 17.5);
        chargeAndDelivery(saved);
        OrderSummaryBilling.Refund refund = new OrderSummaryBilling.Refund(
                "refund-1", 17.5, DELIVERED_AT.plusSeconds(3600), "Order refunded");
        when(billing.findRefunds("order-1")).thenReturn(List.of(refund));

        OrderSummary result = service.getSummary("session-1", "order-1");

        assertSame(saved, result.pricing());
        assertEquals(DELIVERED_AT, result.deliveredAt());
        assertEquals(List.of(refund), result.refunds());
    }

    @Test
    void returnsOriginalSummaryWhenRefundDetailsAreNotAvailable() {
        customerSession();
        eligibleOrder("refunded");
        chargeAndDelivery(new PriceBreakdown(12, 4, 1.5, 0, 17.5));

        assertEquals(List.of(), service.getSummary("session-1", "order-1").refunds());
    }

    @Test
    void refusesMissingSessionBeforeReadingOrderOrBilling() {
        when(sessionManager.findSession(null)).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getSummary(null, "order-1"));

        assertEquals("Authentication required", error.getMessage());
        verifyNoInteractions(orderRegistry, eventRepository, billing);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"RESTAURANT", "COURIER", "OPERATOR"})
    void refusesOtherRolesBeforeReadingOrder(Role role) {
        when(sessionManager.findSession("session-1"))
                .thenReturn(Optional.of(new Session("session-1", "customer-1", role)));

        assertThrows(SecurityException.class, () -> service.getSummary("session-1", "order-1"));

        verifyNoInteractions(orderRegistry, eventRepository, billing);
    }

    @Test
    void reportsAnotherCustomersOrderAsNotFoundBeforeReadingItsPrices() {
        customerSession();
        Order order = new Order("customer-2", "bakery-1", "Other customer", "Montreal");
        when(orderRegistry.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(OrderNotFoundException.class, () -> service.getSummary("session-1", "order-1"));

        verifyNoInteractions(eventRepository, billing);
    }

    @Test
    void reportsNonexistentOrderAsNotFound() {
        customerSession();
        when(orderRegistry.findById("missing")).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> service.getSummary("session-1", "missing"));

        verifyNoInteractions(eventRepository, billing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"placed", "accepted", "preparing", "ready", "picked_up", "cancelled", "rejected"})
    void refusesOrdersThatHaveNotBeenDelivered(String status) {
        customerSession();
        eligibleOrder(status);

        assertThrows(IllegalStateException.class, () -> service.getSummary("session-1", "order-1"));

        verifyNoInteractions(eventRepository, billing);
    }

    @Test
    void reportsMissingBillingIntegrationWithoutCreatingACharge() {
        customerSession();
        eligibleOrder("delivered");
        service = new OrderSummaryService(sessionManager, orderRegistry, eventRepository, Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getSummary("session-1", "order-1"));

        assertEquals("Order summary is unavailable until billing integration is connected", error.getMessage());
        verifyNoInteractions(eventRepository, billing);
    }

    @Test
    void refusesOrderWithoutRecordedCharge() {
        customerSession();
        eligibleOrder("delivered");
        when(billing.findCharge("order-1")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.getSummary("session-1", "order-1"));

        verifyNoInteractions(eventRepository);
    }

    @Test
    void refusesChargeWithoutSavedBreakdown() {
        customerSession();
        eligibleOrder("delivered");
        when(billing.findCharge("order-1"))
                .thenReturn(Optional.of(new OrderSummaryBilling.Charge("charge-1", null)));

        assertThrows(IllegalStateException.class, () -> service.getSummary("session-1", "order-1"));

        verifyNoInteractions(eventRepository);
    }

    @Test
    void refusesMissingDeliveryEventInsteadOfUsingCurrentTime() {
        customerSession();
        eligibleOrder("delivered");
        when(billing.findCharge("order-1")).thenReturn(Optional.of(new OrderSummaryBilling.Charge(
                "charge-1", new PriceBreakdown(12, 4, 1.5, 0, 17.5))));
        when(eventRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, "order-1"))
                .thenReturn(List.of(new StateTransitionEvent("event-1", EntityType.ORDER, "order-1",
                        "ready", "picked_up", DELIVERED_AT)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getSummary("session-1", "order-1"));

        assertEquals("Order summary is not available: delivery timestamp is missing", error.getMessage());
    }

    private void customerSession() {
        when(sessionManager.findSession("session-1"))
                .thenReturn(Optional.of(new Session("session-1", "customer-1", Role.CUSTOMER)));
    }

    private void eligibleOrder(String status) {
        Order order = mock(Order.class);
        when(order.getCustomerId()).thenReturn("customer-1");
        when(order.getStatus()).thenReturn(status);
        when(orderRegistry.findById("order-1")).thenReturn(Optional.of(order));
    }

    private void chargeAndDelivery(PriceBreakdown saved) {
        when(billing.findCharge("order-1"))
                .thenReturn(Optional.of(new OrderSummaryBilling.Charge("charge-1", saved)));
        when(eventRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, "order-1"))
                .thenReturn(List.of(
                        new StateTransitionEvent("pickup", EntityType.ORDER, "order-1", "ready", "picked_up",
                                DELIVERED_AT.minusSeconds(600)),
                        new StateTransitionEvent("delivery", EntityType.ORDER, "order-1", "picked_up", "delivered",
                                DELIVERED_AT),
                        new StateTransitionEvent("refund", EntityType.ORDER, "order-1", "delivered", "refunded",
                                DELIVERED_AT.plusSeconds(3600))));
    }
}
