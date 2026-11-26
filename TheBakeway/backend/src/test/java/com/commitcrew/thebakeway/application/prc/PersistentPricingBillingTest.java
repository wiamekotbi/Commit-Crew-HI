package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.LedgerEntryType;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

@ExtendWith(MockitoExtension.class)
class PersistentPricingBillingTest {

    @Mock
    private OrderLedgerRepository ledgerRepository;

    @Test
    void recordsOneChargeWithThePricingSnapshotAndDeliveryTime() {
        PersistentPricingBilling billing = new PersistentPricingBilling(ledgerRepository);
        Order order = order();
        PriceBreakdown pricing = new PriceBreakdown(12, 4, 2, 3, 21);
        Instant deliveredAt = Instant.parse("2026-11-06T12:00:00Z");
        when(ledgerRepository.findByOrder_IdAndEntryType("order-1", LedgerEntryType.CHARGE))
                .thenReturn(Optional.empty());

        assertTrue(billing.recordChargeIfAbsent(order, pricing, deliveredAt));

        ArgumentCaptor<OrderLedgerEntry> entry = ArgumentCaptor.forClass(OrderLedgerEntry.class);
        verify(ledgerRepository).save(entry.capture());
        assertEquals(LedgerEntryType.CHARGE, entry.getValue().getEntryType());
        assertEquals(21, entry.getValue().getAmount());
        assertEquals(pricing, entry.getValue().getPricing());
        assertEquals(deliveredAt, entry.getValue().getOccurredAt());
        assertEquals("CAD", entry.getValue().getCurrency());
    }

    @Test
    void doesNotRecordDuplicateCharge() {
        PersistentPricingBilling billing = new PersistentPricingBilling(ledgerRepository);
        when(ledgerRepository.findByOrder_IdAndEntryType("order-1", LedgerEntryType.CHARGE))
                .thenReturn(Optional.of(OrderLedgerEntry.charge(order(),
                        new PriceBreakdown(5, 4, 2, 0, 11), Instant.now())));

        assertFalse(billing.recordChargeIfAbsent(order(), new PriceBreakdown(5, 4, 2, 0, 11), Instant.now()));

        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void returnsRefundsAndLedgerEntriesInTimestampOrder() {
        PersistentPricingBilling billing = new PersistentPricingBilling(ledgerRepository);
        Order order = order();
        Instant chargedAt = Instant.parse("2026-11-06T12:00:00Z");
        Instant refundedAt = chargedAt.plusSeconds(60);
        OrderLedgerEntry charge = OrderLedgerEntry.charge(order,
                new PriceBreakdown(5, 4, 2, 0, 11), chargedAt);
        OrderLedgerEntry refund = OrderLedgerEntry.refund(order, 11, refundedAt,
                "operator-1", "Customer request");
        when(ledgerRepository.findByOrder_IdOrderByOccurredAtAsc("order-1")).thenReturn(List.of(charge, refund));

        assertEquals(List.of(new OrderSummaryBilling.Refund(null, 11, refundedAt, "Customer request")),
                billing.findRefunds("order-1"));
    }

    @Test
    void readsLegacyChargeWithoutPromoColumnsAsZeroDiscount() {
        OrderLedgerEntry entry = OrderLedgerEntry.charge(order(),
                new PriceBreakdown(5, 4, 2, 0, 11), Instant.now());
        ReflectionTestUtils.setField(entry, "promoCode", null);
        ReflectionTestUtils.setField(entry, "promoDiscountAmount", null);

        assertEquals(0, entry.getPricing().discountAmount());
        assertEquals(java.math.BigDecimal.ZERO, entry.getPromoDiscountAmount());
    }

    private Order order() {
        Order order = new Order("customer-1", "bakery-1", "Customer", "Montreal");
        ReflectionTestUtils.setField(order, "id", "order-1");
        return order;
    }
}