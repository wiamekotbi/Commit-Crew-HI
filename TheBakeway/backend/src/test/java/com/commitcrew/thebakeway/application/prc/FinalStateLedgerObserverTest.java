package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.LedgerEntryType;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;

@ExtendWith(MockitoExtension.class)
class FinalStateLedgerObserverTest {

    private static final String ORDER_ID = "order-1";
    private static final Instant CLOSED_AT = Instant.parse("2026-11-13T15:00:00Z");

    @Mock
    private OrderRegistry orders;

    @Mock
    private OrderLedgerRepository ledger;

    private final CommandConsole console = new CommandConsole();
    private FinalStateLedgerObserver observer;

    @BeforeEach
    void setUp() {
        observer = new FinalStateLedgerObserver(orders, ledger, console);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "cancelled"})
    void recordsARejectedOrCancelledOrderWithAZeroCharge(String finalState) {
        Order order = order();
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(ledger.findByOrder_IdAndEntryType(ORDER_ID, LedgerEntryType.CHARGE)).thenReturn(Optional.empty());

        observer.update(event(EntityType.ORDER, finalState));

        ArgumentCaptor<OrderLedgerEntry> entry = ArgumentCaptor.forClass(OrderLedgerEntry.class);
        verify(ledger).save(entry.capture());
        assertEquals(LedgerEntryType.CHARGE, entry.getValue().getEntryType());
        assertEquals(0.0, entry.getValue().getAmount());
        assertEquals(finalState, entry.getValue().getFinalState());
        assertEquals(CLOSED_AT, entry.getValue().getOccurredAt());
        assertNull(entry.getValue().getPricing());
        assertEquals("RECORD_ORDER_IN_LEDGER", console.entriesAfter(0).get(0).command());
    }

    @Test
    void recordsAnOrderOnlyOnce() {
        when(orders.findById(ORDER_ID)).thenReturn(Optional.of(order()));
        when(ledger.findByOrder_IdAndEntryType(ORDER_ID, LedgerEntryType.CHARGE))
                .thenReturn(Optional.of(OrderLedgerEntry.noCharge(order(), "cancelled", CLOSED_AT)));

        observer.update(event(EntityType.ORDER, "cancelled"));

        verify(ledger, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"accepted", "ready", "picked_up", "delivered", "refunded"})
    void ignoresOtherStates(String newState) {
        observer.update(event(EntityType.ORDER, newState));

        verifyNoInteractions(orders, ledger);
    }

    @Test
    void ignoresOtherEntities() {
        observer.update(event(EntityType.BAKERY, "cancelled"));

        verifyNoInteractions(orders, ledger);
    }

    private Order order() {
        Order order = new Order("customer-1", "bakery-1", "Maria", "Montreal");
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private StateTransitionEvent event(EntityType entityType, String newState) {
        return new StateTransitionEvent("event-1", entityType, ORDER_ID, "placed", newState, CLOSED_AT);
    }
}
