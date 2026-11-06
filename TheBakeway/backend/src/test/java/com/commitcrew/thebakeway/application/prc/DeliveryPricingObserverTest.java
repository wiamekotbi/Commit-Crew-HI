package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

@ExtendWith(MockitoExtension.class)
class DeliveryPricingObserverTest {

    private static final Instant DELIVERED_AT = Instant.parse("2026-11-05T15:00:00Z");
    private static final PriceBreakdown PRICE = new PriceBreakdown(8, 3, 1, 2, 14);
    @Mock
    private OrderRegistry orders;
    @Mock
    private PricingService pricing;
    @Mock
    private PricingBilling billing;
    private CommandConsole console;
    private DeliveryPricingObserver observer;

    @BeforeEach
    void setUp() {
        console = new CommandConsole();
        observer = new DeliveryPricingObserver(orders, pricing, Optional.of(billing), console);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deliveredEventUsesExistingPricingServiceAndPassesOrderAndActualTimestampToBilling() {
        Order order = deliveredOrder(2);
        when(pricing.calculatePrice(order)).thenReturn(PRICE);
        when(billing.recordChargeIfAbsent(order, PRICE, DELIVERED_AT)).thenReturn(true);

        observer.update(event(EntityType.ORDER, "delivered"));

        verify(pricing).calculatePrice(order);
        verify(billing).recordChargeIfAbsent(order, PRICE, DELIVERED_AT);
        assertTrue(console.entriesAfter(0).get(0).success());
        assertEquals("COMPUTE_ORDER_CHARGE", console.entriesAfter(0).get(0).command());
    }

    @ParameterizedTest
    @ValueSource(strings = {"placed", "accepted", "preparing", "ready", "picked_up", "cancelled", "rejected", "refunded"})
    void ignoresOtherOrderTransitions(String state) {
        observer.update(event(EntityType.ORDER, state));

        verifyNoInteractions(orders, pricing, billing);
        assertTrue(console.entriesAfter(0).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = EntityType.class, names = {"ORDER"}, mode = EnumSource.Mode.EXCLUDE)
    void ignoresNonOrderEvents(EntityType entityType) {
        observer.update(event(entityType, "delivered"));

        verifyNoInteractions(orders, pricing, billing);
    }

    @Test
    void refusesAnEventForAnOrderThatIsNotDelivered() {
        Order order = new Order("customer-1", "bakery-1", "Maria", "Montreal");
        when(orders.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> observer.update(event(EntityType.ORDER, "delivered")));

        verifyNoInteractions(pricing, billing);
        assertFalse(console.entriesAfter(0).get(0).success());
    }

    @Test
    void refusesMissingOrder() {
        assertThrows(OrderNotFoundException.class, () -> observer.update(event(EntityType.ORDER, "delivered")));

        verifyNoInteractions(pricing, billing);
    }

    @Test
    void repeatedDeliveryEventDoesNotRecalculateOrOverwriteAnExistingCharge() {
        Order order = deliveredOrder(2);
        when(pricing.calculatePrice(order)).thenReturn(PRICE);
        when(billing.findCharge("order-1")).thenReturn(Optional.empty(),
                Optional.of(new OrderSummaryBilling.Charge("charge-1", PRICE)));
        when(billing.recordChargeIfAbsent(order, PRICE, DELIVERED_AT)).thenReturn(true);

        observer.update(event(EntityType.ORDER, "delivered"));
        observer.update(event(EntityType.ORDER, "delivered"));

        verify(pricing, times(1)).calculatePrice(order);
        verify(billing, times(1)).recordChargeIfAbsent(order, PRICE, DELIVERED_AT);
        assertFalse(console.entriesAfter(0).get(1).success());
        assertTrue(console.entriesAfter(0).get(1).detail().contains("already has a charge"));
    }

    @Test
    void handlesAnotherWriterRecordingTheChargeFirst() {
        Order order = deliveredOrder(2);
        when(pricing.calculatePrice(order)).thenReturn(PRICE);

        observer.update(event(EntityType.ORDER, "delivered"));

        verify(billing).recordChargeIfAbsent(order, PRICE, DELIVERED_AT);
        assertFalse(console.entriesAfter(0).get(0).success());
    }

    @Test
    void missingBillingIsExplicitAndDoesNotPreventDeliveryOrInventACharge() {
        Order order = deliveredOrder(2);
        when(pricing.calculatePrice(order)).thenReturn(PRICE);
        observer = new DeliveryPricingObserver(orders, pricing, Optional.empty(), console);

        observer.update(event(EntityType.ORDER, "delivered"));

        verify(pricing).calculatePrice(order);
        verifyNoInteractions(billing);
        assertEquals("delivered", order.getStatus());
        assertFalse(console.entriesAfter(0).get(0).success());
        assertTrue(console.entriesAfter(0).get(0).detail().contains("no charge recorded"));
    }

    @ParameterizedTest
    @CsvSource({"2, 8, 2, 14", "3, 12, 0, 16", "4, 16, 0, 20"})
    void realPricingUsesSavedUnitPricesAndQuantitiesWithConfiguredFeesAndThreshold(
            int quantity, double subtotal, double surcharge, double total) {
        Order order = deliveredOrder(quantity);
        PricingConfigurationReader reader = mock(PricingConfigurationReader.class);
        when(reader.read()).thenReturn(new PricingConfiguration(3, 1, 12, 2));
        observer = new DeliveryPricingObserver(orders, new PricingService(reader), Optional.of(billing), console);
        PriceBreakdown expected = new PriceBreakdown(subtotal, 3, 1, surcharge, total);

        observer.update(event(EntityType.ORDER, "delivered"));

        verify(billing).recordChargeIfAbsent(order, expected, DELIVERED_AT);
        verify(reader).read();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Missing pricing value: deliveryFee", "Invalid pricing value: serviceFee"})
    void invalidConfigurationDoesNotCreateAChargeAndPropagatesError(String message) {
        deliveredOrder(2);
        PricingConfigurationReader reader = mock(PricingConfigurationReader.class);
        when(reader.read()).thenThrow(new IllegalArgumentException(message));
        observer = new DeliveryPricingObserver(orders, new PricingService(reader), Optional.of(billing), console);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> observer.update(event(EntityType.ORDER, "delivered")));

        assertEquals(message, error.getMessage());
        verify(billing, never()).recordChargeIfAbsent(any(), any(), any());
        assertFalse(console.entriesAfter(0).get(0).success());
    }

    @Test
    void missingConfigurationFileDoesNotCreateACharge() {
        Order order = deliveredOrder(2);
        when(pricing.calculatePrice(order)).thenThrow(new IllegalStateException("Cannot read pricing configuration"));

        assertThrows(IllegalStateException.class, () -> observer.update(event(EntityType.ORDER, "delivered")));

        verify(billing, never()).recordChargeIfAbsent(any(), any(), any());
        assertTrue(console.entriesAfter(0).get(0).detail().contains("Cannot read pricing configuration"));
    }

    @Test
    void billingFailureIsLoggedAndPropagated() {
        Order order = deliveredOrder(2);
        when(pricing.calculatePrice(order)).thenReturn(PRICE);
        when(billing.recordChargeIfAbsent(order, PRICE, DELIVERED_AT))
                .thenThrow(new IllegalStateException("Charge could not be saved"));

        assertThrows(IllegalStateException.class, () -> observer.update(event(EntityType.ORDER, "delivered")));

        assertFalse(console.entriesAfter(0).get(0).success());
    }

    @Test
    void successfulChargeIsLoggedOnlyAfterCommit() {
        Order order = deliveredOrder(2);
        when(pricing.calculatePrice(order)).thenReturn(PRICE);
        when(billing.recordChargeIfAbsent(order, PRICE, DELIVERED_AT)).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        observer.update(event(EntityType.ORDER, "delivered"));

        assertTrue(console.entriesAfter(0).isEmpty());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertTrue(console.entriesAfter(0).get(0).success());
    }

    @Test
    void rolledBackChargeDoesNotLogSuccess() {
        Order order = deliveredOrder(2);
        when(pricing.calculatePrice(order)).thenReturn(PRICE);
        when(billing.recordChargeIfAbsent(order, PRICE, DELIVERED_AT)).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();

        observer.update(event(EntityType.ORDER, "delivered"));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertTrue(console.entriesAfter(0).isEmpty());
    }

    private Order deliveredOrder(int quantity) {
        Order order = new Order("customer-1", "bakery-1", "Maria", "Montreal");
        ReflectionTestUtils.setField(order, "id", "order-1");
        MenuItem item = new MenuItem("bakery-1", "Croissant", "", 4, MenuItemStatus.available);
        order.addOrderItem(item, quantity);
        item.updateDetails("Changed menu item", "", 100);
        order.accept();
        order.startPreparing();
        order.markReady();
        order.confirmPickup();
        order.confirmDelivery();
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        return order;
    }

    private StateTransitionEvent event(EntityType entityType, String state) {
        return new StateTransitionEvent("event-1", entityType, "order-1", "picked_up", state, DELIVERED_AT);
    }
}
