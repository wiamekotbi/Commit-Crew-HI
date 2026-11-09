package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.courier.CourierDelivery;
import com.commitcrew.thebakeway.domain.courier.CourierDeliveryRepository;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.LedgerEntryType;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;

class CourierHistoryServiceTest {

    private final CourierDeliveryRepository deliveries = mock(CourierDeliveryRepository.class);
    private final OrderLedgerRepository ledger = mock(OrderLedgerRepository.class);
    private final CourierHistoryService service = new CourierHistoryService(deliveries, ledger);

    @Test
    void includesLedgerEntriesForEachCompletedDelivery() {
        Instant deliveredAt = Instant.parse("2026-11-07T12:00:00Z");
        Order order = mock(Order.class);
        when(order.getId()).thenReturn("order-1");
        CourierDelivery delivery = mock(CourierDelivery.class);
        when(delivery.getOrder()).thenReturn(order);
        when(delivery.getDeliveredAt()).thenReturn(deliveredAt);
        when(deliveries.findByCourier_CourierIdOrderByDeliveredAtDesc("courier-1"))
                .thenReturn(List.of(delivery));

        OrderLedgerEntry charge = mock(OrderLedgerEntry.class);
        when(charge.getId()).thenReturn("ledger-1");
        when(charge.getOrder()).thenReturn(order);
        when(charge.getEntryType()).thenReturn(LedgerEntryType.CHARGE);
        when(charge.getAmount()).thenReturn(17.0);
        when(charge.getCurrency()).thenReturn("CAD");
        when(charge.getOccurredAt()).thenReturn(deliveredAt);
        when(ledger.findByOrder_IdInOrderByOccurredAtAsc(List.of("order-1")))
                .thenReturn(List.of(charge));

        List<CompletedDeliveryView> result = service.getPastDeliveries("courier-1");

        assertEquals(1, result.size());
        assertEquals("order-1", result.get(0).orderId());
        assertEquals(deliveredAt, result.get(0).deliveredAt());
        assertEquals("CHARGE", result.get(0).ledgerEntries().get(0).entryType());
        assertEquals(17.0, result.get(0).ledgerEntries().get(0).amount());
        verify(deliveries).findByCourier_CourierIdOrderByDeliveredAtDesc("courier-1");
    }

    @Test
    void avoidsLedgerQueryWhenCourierHasNoPastDeliveries() {
        when(deliveries.findByCourier_CourierIdOrderByDeliveredAtDesc("courier-1"))
                .thenReturn(List.of());

        assertEquals(List.of(), service.getPastDeliveries("courier-1"));
    }
}
