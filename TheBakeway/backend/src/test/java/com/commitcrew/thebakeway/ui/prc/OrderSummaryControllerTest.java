package com.commitcrew.thebakeway.ui.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.ord.BakeryStaffAccessService;
import com.commitcrew.thebakeway.application.ord.OrderAccessPolicy;
import com.commitcrew.thebakeway.application.prc.OrderSummaryBilling;
import com.commitcrew.thebakeway.application.prc.OrderSummaryService;
import com.commitcrew.thebakeway.application.prc.OrderBillingService;
import com.commitcrew.thebakeway.application.prc.OrderLedgerEntryView;
import com.commitcrew.thebakeway.application.prc.OrderPricingQuote;
import com.commitcrew.thebakeway.application.prc.OrderPricingService;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderLine;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
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
    private OrderBillingService orderBillingService;
    @Mock
    private OrderPricingService orderPricingService;
    @Mock
    private AccountRegistry accountRegistry;

    private final SessionManager sessions = new SessionManager();
    private final CommandConsole console = new CommandConsole();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        OrderSummaryService service = new OrderSummaryService(sessions, orderRegistry, eventRepository,
                Optional.of(billing), new OrderAccessPolicy(mock(BakeryStaffAccessService.class)));
        mvc = MockMvcBuilders.standaloneSetup(
                new OrderSummaryController(service, orderBillingService, orderPricingService))
                .addFilters(new CommandConsoleFilter(console, sessions, accountRegistry)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"delivered", "refunded"})
    void returnsOriginalItemsFromRealServiceAndLogsSuccessfulCommand(String orderStatus) throws Exception {
        Order order = order(orderStatus, "customer-1");
        when(order.getId()).thenReturn("order-1");
        MenuItem item = new MenuItem("bakery-1", "Original croissant", "", 4, MenuItemStatus.available);
        OrderLine line = new OrderLine(order, item, 3);
        when(order.getOrderItems()).thenReturn(List.of(line));
        item.updateDetails("Changed menu item", "", 100);
        when(billing.findCharge("order-1")).thenReturn(Optional.of(new OrderSummaryBilling.Charge(
                "charge-1", new PriceBreakdown(12, 4, 1, 2, 19))));
        when(eventRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, "order-1"))
                .thenReturn(List.of(new StateTransitionEvent("delivery-1", EntityType.ORDER,
                        "order-1", "picked_up", "delivered", Instant.parse("2026-11-02T15:00:00Z"))));

        mvc.perform(get("/api/orders/order-1/summary").cookie(session(Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.status").value(orderStatus))
                .andExpect(jsonPath("$.items[0].itemName").value("Original croissant"))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].unitPrice").value(4))
                .andExpect(jsonPath("$.items[0].subtotal").value(12))
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
    void orderSummaryApiIncludesThePromotionSavedOnTheCharge() throws Exception {
        Order order = order("delivered", "customer-1");
        when(order.getId()).thenReturn("order-1");
        when(order.getOrderItems()).thenReturn(List.of());
        when(billing.findCharge("order-1")).thenReturn(Optional.of(new OrderSummaryBilling.Charge(
                "charge-1", new PriceBreakdown(25, 4, 2, 0, 26, 5),
                "SAVE5", new BigDecimal("5.00"))));
        when(eventRepository.findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, "order-1"))
                .thenReturn(List.of(new StateTransitionEvent("delivery-1", EntityType.ORDER,
                        "order-1", "picked_up", "delivered", Instant.parse("2026-11-02T15:00:00Z"))));

        mvc.perform(get("/api/orders/order-1/summary").cookie(session(Role.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promoCode").value("SAVE5"))
                .andExpect(jsonPath("$.discountAmount").value(5))
                .andExpect(jsonPath("$.pricing.total").value(26));
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

    @Test
    void returnsRoleScopedLedgerRows() throws Exception {
        Cookie operator = session(Role.OPERATOR);
        when(orderBillingService.getLedger(operator.getValue(), "order-1")).thenReturn(List.of(
                new OrderLedgerEntryView("charge-1", "order-1", "CHARGE", 19, "CAD",
                        Instant.parse("2026-11-02T15:00:00Z"), null, null,
                        new PriceBreakdown(12, 4, 1, 2, 19), "delivered", null)));

        mvc.perform(get("/api/orders/order-1/ledger").cookie(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ledgerEntryId").value("charge-1"))
                .andExpect(jsonPath("$[0].entryType").value("CHARGE"))
                .andExpect(jsonPath("$[0].amount").value(19))
                .andExpect(jsonPath("$[0].pricing.total").value(19));
    }

    @Test
    void acceptsOperatorRefundCommand() throws Exception {
        Cookie operator = session(Role.OPERATOR);
        when(orderBillingService.refundDeliveredOrder(operator.getValue(), "order-1", "Customer request"))
                .thenReturn(new OrderLedgerEntryView("refund-1", "order-1", "REFUND", 19, "CAD",
                        Instant.parse("2026-11-02T16:00:00Z"), "customer-1", "Customer request", null,
                        "refunded", null));

        mvc.perform(post("/api/orders/order-1/refund").cookie(operator)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerEntryId").value("refund-1"))
                .andExpect(jsonPath("$.entryType").value("REFUND"))
                .andExpect(jsonPath("$.amount").value(19))
                .andExpect(jsonPath("$.reason").value("Customer request"));
    }

    @Test
    void returnsCartPriceQuoteFromPricingService() throws Exception {
        Cookie customer = session(Role.CUSTOMER);
        when(orderPricingService.quoteCart(customer.getValue(), 8, null))
                .thenReturn(new OrderPricingQuote(new PriceBreakdown(8, 4, 2, 3, 17), false));

        mvc.perform(get("/api/orders/pricing").cookie(customer).param("subtotal", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalFare").value(false))
                .andExpect(jsonPath("$.pricing.deliveryFee").value(4))
                .andExpect(jsonPath("$.pricing.serviceFee").value(2))
                .andExpect(jsonPath("$.pricing.smallOrderSurcharge").value(3))
                .andExpect(jsonPath("$.pricing.total").value(17))
                .andExpect(jsonPath("$.promoCode").doesNotExist())
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.discountedTotal").value(17));
    }

    @Test
    void returnsDiscountedCartPriceQuoteForValidPromotion() throws Exception {
        Cookie customer = session(Role.CUSTOMER);
        when(orderPricingService.quoteCart(customer.getValue(), 25, "SAVE5"))
                .thenReturn(new OrderPricingQuote(
                        new PriceBreakdown(25, 4, 2, 0, 31),
                        false,
                        "SAVE5",
                        new BigDecimal("5.00"),
                        new BigDecimal("26.00")));

        mvc.perform(get("/api/orders/pricing").cookie(customer)
                        .param("subtotal", "25")
                        .param("promoCode", "SAVE5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.total").value(31))
                .andExpect(jsonPath("$.promoCode").value("SAVE5"))
                .andExpect(jsonPath("$.discountAmount").value(5))
                .andExpect(jsonPath("$.discountedTotal").value(26));
    }

    @Test
    void returnsBadRequestWhenPromotionIsRejected() throws Exception {
        Cookie customer = session(Role.CUSTOMER);
        when(orderPricingService.quoteCart(customer.getValue(), 25, "NOTREAL"))
                .thenThrow(new IllegalArgumentException("Unknown promo code. Please enter a valid one."));

        mvc.perform(get("/api/orders/pricing").cookie(customer)
                        .param("subtotal", "25")
                        .param("promoCode", "NOTREAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown promo code. Please enter a valid one."));
    }

    @Test
    void returnsOrderPricingQuoteFromPricingService() throws Exception {
        Cookie operator = session(Role.OPERATOR);
        when(orderPricingService.quoteOrder(operator.getValue(), "order-1"))
                .thenReturn(new OrderPricingQuote(new PriceBreakdown(20, 4, 2, 0, 26), true));

        mvc.perform(get("/api/orders/order-1/pricing").cookie(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalFare").value(true))
                .andExpect(jsonPath("$.pricing.subtotal").value(20))
                .andExpect(jsonPath("$.pricing.total").value(26));
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
