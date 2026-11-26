package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.ord.BakeryStaffAccessService;
import com.commitcrew.thebakeway.application.ord.OrderAccessPolicy;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

class OrderPromotionServiceTest {

    @Test
    void promotionSnapshotFlowsIntoFinalChargeLedger() {
        PricingConfigurationReader pricingReader = mock(PricingConfigurationReader.class);
        when(pricingReader.read()).thenReturn(new PricingConfiguration(4, 2, 20, 3));
        PromoConfigurationReader promoReader = mock(PromoConfigurationReader.class);
        when(promoReader.read()).thenReturn(List.of(new PromoConfiguration(
                "SAVE5", LocalDate.of(2027, 12, 31), new BigDecimal("20.00"), new BigDecimal("5.00"))));
        PromoValidationService validation = new PromoValidationService(Clock.fixed(
                Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC));
        PricingService pricing = new PricingService(pricingReader);
        OrderPromotionService promotions = new OrderPromotionService(
                pricing, new PromoDiscountService(promoReader, validation));

        Order order = new Order("customer-1", "bakery-1", "Customer", "Montreal");
        ReflectionTestUtils.setField(order, "id", "order-1");
        order.addOrderItem(new MenuItem("bakery-1", "Cake", "", 25, MenuItemStatus.available), 1);
        promotions.apply(order, "save5");
        order.accept();
        order.startPreparing();
        order.markReady();
        order.confirmPickup();
        order.confirmDelivery();

        PriceBreakdown actualCharge = pricing.calculatePrice(order);
        OrderLedgerRepository ledgerRepository = mock(OrderLedgerRepository.class);
        Instant deliveredAt = Instant.parse("2026-11-25T13:00:00Z");
        OrderLedgerEntry ledgerEntry = OrderLedgerEntry.charge(order, actualCharge, deliveredAt);
        ReflectionTestUtils.setField(ledgerEntry, "id", "charge-1");
        when(ledgerRepository.findByOrder_IdAndEntryType("order-1",
                com.commitcrew.thebakeway.domain.pricing.LedgerEntryType.CHARGE))
                .thenReturn(Optional.empty(), Optional.of(ledgerEntry));
        when(ledgerRepository.save(any(OrderLedgerEntry.class))).thenReturn(ledgerEntry);
        PersistentPricingBilling billing = new PersistentPricingBilling(ledgerRepository);
        billing.recordChargeIfAbsent(order, actualCharge, deliveredAt);
        when(ledgerRepository.findByOrder_IdOrderByOccurredAtAsc("order-1"))
                .thenReturn(List.of(ledgerEntry));

        SessionManager sessions = new SessionManager();
        String customerSession = sessions.createSession("customer-1", Role.CUSTOMER).getSessionId();
        OrderRegistry orders = mock(OrderRegistry.class);
        when(orders.findById("order-1")).thenReturn(Optional.of(order));
        EventRepository events = mock(EventRepository.class);
        when(events.findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, "order-1"))
                .thenReturn(List.of(new StateTransitionEvent("delivered-1", EntityType.ORDER,
                        "order-1", "picked_up", "delivered", deliveredAt)));
        OrderSummaryService summaries = new OrderSummaryService(
                sessions, orders, events, Optional.of(billing),
                new OrderAccessPolicy(mock(BakeryStaffAccessService.class)));
        OrderSummary summary = summaries.getSummary(customerSession, "order-1");

        assertEquals("SAVE5", order.getPromoCode());
        assertEquals(new BigDecimal("5.00"), order.getPromoDiscountAmount());
        assertEquals(26, actualCharge.total());
        assertEquals(5, actualCharge.discountAmount());
        assertEquals(26, ledgerEntry.getAmount());
        assertEquals("SAVE5", ledgerEntry.getPromoCode());
        assertEquals(new BigDecimal("5.00"), ledgerEntry.getPromoDiscountAmount());
        assertEquals(26, ledgerEntry.getPricing().total());
        assertEquals(5, ledgerEntry.getPricing().discountAmount());
        assertEquals("SAVE5", summary.promoCode());
        assertEquals(new BigDecimal("5.00"), summary.discountAmount());
        assertEquals(26, summary.pricing().total());
        verify(ledgerRepository).save(any(OrderLedgerEntry.class));
    }

    @Test
    void refusesInvalidPromoBeforeItCanBeSavedOnAnOrder() {
        PromoConfigurationReader promoReader = mock(PromoConfigurationReader.class);
        when(promoReader.read()).thenReturn(List.of());
        PricingConfigurationReader pricingReader = mock(PricingConfigurationReader.class);
        when(pricingReader.read()).thenReturn(new PricingConfiguration(4, 2, 20, 3));
        PricingService pricing = new PricingService(pricingReader);
        OrderPromotionService promotions = new OrderPromotionService(
                pricing, new PromoDiscountService(promoReader, mock(PromoValidationService.class)));
        Order order = new Order("customer-1", "bakery-1", "Customer", "Montreal");

        assertThrows(IllegalArgumentException.class, () -> promotions.apply(order, "NOTREAL"));
        assertNull(order.getPromoCode());
    }
}
