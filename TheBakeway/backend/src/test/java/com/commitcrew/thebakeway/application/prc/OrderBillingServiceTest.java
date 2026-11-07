package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.ord.BakeryStaffAccessService;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.LedgerEntryType;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

@ExtendWith(MockitoExtension.class)
class OrderBillingServiceTest {

    @Mock
    private BakeryStaffAccessService bakeryStaffAccessService;
    @Mock
    private OrderRegistry orderRegistry;
    @Mock
    private OrderLedgerRepository ledgerRepository;

    private final SessionManager sessions = new SessionManager();
    private final PermissionPolicy permissionPolicy = new PermissionPolicy();
    private OrderBillingService service;

    @BeforeEach
    void setUp() {
        service = new OrderBillingService(sessions, permissionPolicy, bakeryStaffAccessService,
                orderRegistry, ledgerRepository);
    }

    @Test
    void operatorRefundsDeliveredOrderAsSeparateFullRefundEntry() {
        String sessionId = sessions.createSession("operator-1", Role.OPERATOR).getSessionId();
        Order order = deliveredOrder();
        when(orderRegistry.findById("order-1")).thenReturn(Optional.of(order));
        OrderLedgerEntry charge = OrderLedgerEntry.charge(order,
                new PriceBreakdown(12, 4, 2, 3, 21), Instant.parse("2026-11-06T12:00:00Z"));
        when(ledgerRepository.findByOrderIdAndEntryTypeForUpdate("order-1", LedgerEntryType.CHARGE))
                .thenReturn(Optional.of(charge));
        when(ledgerRepository.findByOrder_IdAndEntryType("order-1", LedgerEntryType.REFUND))
                .thenReturn(Optional.empty());
        when(ledgerRepository.save(any(OrderLedgerEntry.class))).thenAnswer(call -> {
            OrderLedgerEntry entry = call.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", "refund-1");
            return entry;
        });

        OrderLedgerEntryView refund = service.refundDeliveredOrder(sessionId, "order-1", "Customer request");

        assertEquals("REFUND", refund.entryType());
        assertEquals(21, refund.amount());
        assertEquals("operator-1", refund.actorAccountId());
        assertEquals("Customer request", refund.reason());
        assertEquals("delivered", order.getStatus());
        ArgumentCaptor<OrderLedgerEntry> entry = ArgumentCaptor.forClass(OrderLedgerEntry.class);
        verify(ledgerRepository).save(entry.capture());
        assertEquals(LedgerEntryType.REFUND, entry.getValue().getEntryType());
        assertEquals(21, entry.getValue().getAmount());
        assertEquals("operator-1", entry.getValue().getActorAccountId());
    }

    @Test
    void rejectsNonOperatorRefundBeforeLoadingOrder() {
        String sessionId = sessions.createSession("customer-1", Role.CUSTOMER).getSessionId();

        assertThrows(SecurityException.class,
                () -> service.refundDeliveredOrder(sessionId, "order-1", "Reason"));

        verify(orderRegistry, never()).findById("order-1");
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void rejectsRepeatRefund() {
        String sessionId = sessions.createSession("operator-1", Role.OPERATOR).getSessionId();
        when(orderRegistry.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        when(ledgerRepository.findByOrderIdAndEntryTypeForUpdate("order-1", LedgerEntryType.CHARGE))
                .thenReturn(Optional.of(OrderLedgerEntry.charge(deliveredOrder(),
                        new PriceBreakdown(5, 4, 2, 0, 11), Instant.now())));
        when(ledgerRepository.findByOrder_IdAndEntryType("order-1", LedgerEntryType.REFUND))
                .thenReturn(Optional.of(OrderLedgerEntry.refund(deliveredOrder(), 11, Instant.now(),
                        "operator-1", "Already refunded")));

        assertThrows(IllegalStateException.class,
                () -> service.refundDeliveredOrder(sessionId, "order-1", "Another reason"));

        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void customerLedgerAccessIsLimitedToTheirOwnOrders() {
        String sessionId = sessions.createSession("customer-1", Role.CUSTOMER).getSessionId();
        when(orderRegistry.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        when(ledgerRepository.findByOrder_IdOrderByOccurredAtAsc("order-1")).thenReturn(List.of());

        assertEquals(List.of(), service.getLedger(sessionId, "order-1"));

        when(orderRegistry.findById("order-2")).thenReturn(Optional.of(
                new Order("customer-2", "bakery-1", "Other customer", "Montreal")));
        assertThrows(com.commitcrew.thebakeway.application.ord.OrderNotFoundException.class,
                () -> service.getLedger(sessionId, "order-2"));
    }

    private Order deliveredOrder() {
        Order order = new Order("customer-1", "bakery-1", "Customer", "Montreal");
        ReflectionTestUtils.setField(order, "id", "order-1");
        order.accept();
        order.startPreparing();
        order.markReady();
        order.confirmPickup();
        order.confirmDelivery();
        return order;
    }
}
