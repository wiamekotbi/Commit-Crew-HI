package com.commitcrew.thebakeway.ui.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.prc.OrderSummaryBilling;
import com.commitcrew.thebakeway.application.prc.OrderSummaryService;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;
import com.commitcrew.thebakeway.ui.console.CommandConsoleFilter;

@ExtendWith(MockitoExtension.class)
class OrderSummaryControllerTest {

    @Mock
    private OrderRegistry orderRegistry;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrderSummaryBilling billing;
    @Mock
    private AccountRegistry accountRegistry;

    private final SessionManager sessions = new SessionManager();
    private final CommandConsole console = new CommandConsole();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        OrderSummaryService service = new OrderSummaryService(sessions, orderRegistry, eventRepository,
                Optional.of(billing));
        mvc = MockMvcBuilders.standaloneSetup(new OrderSummaryController(service))
                .addFilters(new CommandConsoleFilter(console, sessions, accountRegistry)).build();
    }

    @Test
    void returnsSummaryFromRealServiceAndLogsSuccessfulCommand() throws Exception {
        Order order = order("delivered", "customer-1");
        when(order.getId()).thenReturn("order-1");
        when(billing.findCharge("order-1")).thenReturn(Optional.of(new OrderSummaryBilling.Charge(
                "charge-1", new PriceBreakdown(12, 4, 1, 2, 19))));
        when(eventRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, "order-1"))
                .thenReturn(List.of(new StateTransitionEvent("delivery-1", EntityType.ORDER,
                        "order-1", "picked_up", "delivered", Instant.parse("2026-11-02T15:00:00Z"))));

        mvc.perform(get("/api/orders/order-1/summary").cookie(session(Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.pricing.subtotal").value(12))
                .andExpect(jsonPath("$.pricing.deliveryFee").value(4))
                .andExpect(jsonPath("$.pricing.serviceFee").value(1))
                .andExpect(jsonPath("$.pricing.smallOrderSurcharge").value(2))
                .andExpect(jsonPath("$.pricing.total").value(19))
                .andExpect(jsonPath("$.deliveredAt").value("2026-11-02T15:00:00Z"))
                .andExpect(jsonPath("$.refunds").isEmpty());

        assertEquals("VIEW_ORDER_SUMMARY", console.entriesAfter(0).get(0).command());
        assertTrue(console.entriesAfter(0).get(0).success());
    }

    @Test
    void rejectsUnauthenticatedRequestsAndLogsError() throws Exception {
        mvc.perform(get("/api/orders/order-1/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));

        assertEquals("VIEW_ORDER_SUMMARY", console.entriesAfter(0).get(0).command());
        assertFalse(console.entriesAfter(0).get(0).success());
        assertTrue(console.entriesAfter(0).get(0).detail().contains("401"));
    }

    @Test
    void refusesOperatorAccess() throws Exception {
        mvc.perform(get("/api/orders/order-1/summary").cookie(session(Role.OPERATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForAnotherCustomersOrder() throws Exception {
        order("delivered", "customer-2");

        mvc.perform(get("/api/orders/order-1/summary").cookie(session(Role.CUSTOMER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForMissingOrder() throws Exception {
        mvc.perform(get("/api/orders/missing/summary").cookie(session(Role.CUSTOMER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesUndeliveredOrder() throws Exception {
        order("preparing", "customer-1");

        mvc.perform(get("/api/orders/order-1/summary").cookie(session(Role.CUSTOMER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Order summary is available only after delivery"));
    }

    @Test
    void refusesMissingCharge() throws Exception {
        order("delivered", "customer-1");

        mvc.perform(get("/api/orders/order-1/summary").cookie(session(Role.CUSTOMER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Order summary is not available: no recorded charge"));
    }

    private Cookie session(Role role) {
        return new Cookie("BAKEWAY_SESSION_ID", sessions.createSession("customer-1", role).getSessionId());
    }

    private Order order(String status, String customerId) {
        Order order = mock(Order.class);
        when(order.getCustomerId()).thenReturn(customerId);
        if ("customer-1".equals(customerId)) {
            when(order.getStatus()).thenReturn(status);
        }
        when(orderRegistry.findById("order-1")).thenReturn(Optional.of(order));
        return order;
    }
}
