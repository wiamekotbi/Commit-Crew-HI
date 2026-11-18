package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.dlv.CourierDeliveryRecorder;
import com.commitcrew.thebakeway.application.dlv.DispatchService;
import com.commitcrew.thebakeway.application.prc.OrderBillingService;
import com.commitcrew.thebakeway.application.prc.OrderPricingService;
import com.commitcrew.thebakeway.application.prc.OrderSummaryService;
import com.commitcrew.thebakeway.application.prc.PricingBilling;
import com.commitcrew.thebakeway.application.prc.PricingService;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.order.IllegalTransitionException;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

// Characterisation tests for refactoring R1 (Switch Statements -> Extract Class).
// They pin, role by role, who may see an order at the five places that check it today:
// OrderBillingService.getLedger, OrderPricingService.quoteOrder, OrderServiceImpl.getOrder,
// OrderServiceImpl.cancelOrder and OrderSummaryService.getSummary. They call the services
// directly, without the permission proxy, so each place's own check is what is tested.
// They must pass on the commit before R1 and on the R1 commit.
class OrderAccessCharacterisationTest {

    private static final String ORDER_ID = "order-1";
    private static final String MISSING_ORDER_ID = "missing-order";
    private static final String OWN_BAKERY = "bakery-1";
    private static final String OTHER_BAKERY = "bakery-2";

    private final SessionManager sessionManager = new SessionManager();
    private final BakeryStaffAccessService bakeryStaffAccess = mock(BakeryStaffAccessService.class);
    private final OrderRegistry orderRegistry = mock(OrderRegistry.class);
    private final OrderLedgerRepository ledgerRepository = mock(OrderLedgerRepository.class);
    private final PricingService pricingService = mock(PricingService.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final PreparationQueue preparationQueue = mock(PreparationQueue.class);

    private OrderBillingService billingService;
    private OrderPricingService pricingQuotes;
    private OrderServiceImpl orderService;
    private OrderSummaryService summaryService;

    private String operator;
    private String ownCustomer;
    private String otherCustomer;
    private String ownStaff;
    private String otherStaff;
    private String courier;

    @BeforeEach
    void setUp() {
        billingService = new OrderBillingService(sessionManager, new PermissionPolicy(), new OrderAccessPolicy(bakeryStaffAccess),
                orderRegistry, ledgerRepository, new TransitionPublisher());
        pricingQuotes = new OrderPricingService(sessionManager, new OrderAccessPolicy(bakeryStaffAccess), orderRegistry,
                pricingService, Optional.<PricingBilling>empty());
        orderService = new OrderServiceImpl(mock(BakeryService.class), mock(MenuService.class), bakeryStaffAccess,
                mock(BakeryStatusAccessService.class), orderRegistry, mock(AccountRegistry.class), sessionManager,
                preparationQueue, new TransitionPublisher(), mock(InitialStateService.class), eventRepository,
                mock(CourierRegistry.class), mock(DispatchService.class), mock(CourierDeliveryRecorder.class),
                new OrderAccessPolicy(bakeryStaffAccess));
        summaryService = new OrderSummaryService(sessionManager, orderRegistry, eventRepository, Optional.empty(),
                new OrderAccessPolicy(bakeryStaffAccess));

        operator = sessionManager.createSession("operator-1", Role.OPERATOR).getSessionId();
        ownCustomer = sessionManager.createSession("customer-1", Role.CUSTOMER).getSessionId();
        otherCustomer = sessionManager.createSession("customer-2", Role.CUSTOMER).getSessionId();
        ownStaff = sessionManager.createSession("staff-1", Role.RESTAURANT).getSessionId();
        otherStaff = sessionManager.createSession("staff-2", Role.RESTAURANT).getSessionId();
        courier = sessionManager.createSession("courier-1", Role.COURIER).getSessionId();

        when(bakeryStaffAccess.getBakeryIdForSession(ownStaff)).thenReturn(OWN_BAKERY);
        when(bakeryStaffAccess.getBakeryIdForSession(otherStaff)).thenReturn(OTHER_BAKERY);
        when(orderRegistry.findById(MISSING_ORDER_ID)).thenReturn(Optional.empty());
        when(orderRegistry.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.findByOrder_IdOrderByOccurredAtAsc(ORDER_ID)).thenReturn(List.of());
        when(pricingService.calculatePrice(any(Order.class))).thenReturn(new PriceBreakdown(7, 4, 2, 3, 16));
        when(preparationQueue.getPreparationQueue(OWN_BAKERY)).thenReturn(List.of());
        givenPlacedOrder();
    }

    // OrderBillingService.getLedger: operator, own customer and own bakery's staff only

    @Test
    void ledgerIsVisibleToTheOperatorTheOwnCustomerAndTheOwnBakery() {
        assertEquals(List.of(), billingService.getLedger(operator, ORDER_ID));
        assertEquals(List.of(), billingService.getLedger(ownCustomer, ORDER_ID));
        assertEquals(List.of(), billingService.getLedger(ownStaff, ORDER_ID));
    }

    @Test
    void ledgerIsNotFoundForAnotherCustomerAnotherBakeryAndACourier() {
        assertThrows(OrderNotFoundException.class, () -> billingService.getLedger(otherCustomer, ORDER_ID));
        assertThrows(OrderNotFoundException.class, () -> billingService.getLedger(otherStaff, ORDER_ID));
        assertThrows(OrderNotFoundException.class, () -> billingService.getLedger(courier, ORDER_ID));
    }

    @Test
    void ledgerOfAMissingOrderIsNotFoundBeforeAnyRoleCheck() {
        assertThrows(OrderNotFoundException.class, () -> billingService.getLedger(ownStaff, MISSING_ORDER_ID));

        verify(bakeryStaffAccess, never()).getBakeryIdForSession(any());
    }

    // OrderPricingService.quoteOrder: same rule as the ledger

    @Test
    void quoteIsVisibleToTheOperatorTheOwnCustomerAndTheOwnBakery() {
        assertEquals(16, pricingQuotes.quoteOrder(operator, ORDER_ID).pricing().total());
        assertEquals(16, pricingQuotes.quoteOrder(ownCustomer, ORDER_ID).pricing().total());
        assertEquals(16, pricingQuotes.quoteOrder(ownStaff, ORDER_ID).pricing().total());
    }

    @Test
    void quoteIsNotFoundForAnotherCustomerAnotherBakeryAndACourier() {
        assertThrows(OrderNotFoundException.class, () -> pricingQuotes.quoteOrder(otherCustomer, ORDER_ID));
        assertThrows(OrderNotFoundException.class, () -> pricingQuotes.quoteOrder(otherStaff, ORDER_ID));
        assertThrows(OrderNotFoundException.class, () -> pricingQuotes.quoteOrder(courier, ORDER_ID));
    }

    @Test
    void quoteOfAMissingOrderIsNotFoundBeforeAnyRoleCheck() {
        assertThrows(OrderNotFoundException.class, () -> pricingQuotes.quoteOrder(ownStaff, MISSING_ORDER_ID));

        verify(bakeryStaffAccess, never()).getBakeryIdForSession(any());
    }

    // OrderServiceImpl.getOrder: only customers and staff are limited; every other role passes

    @Test
    void orderDetailsAreVisibleToTheOperatorTheOwnCustomerTheOwnBakeryAndACourier() {
        assertEquals(ORDER_ID, orderService.getOrder(operator, ORDER_ID).orderId());
        assertEquals(ORDER_ID, orderService.getOrder(ownCustomer, ORDER_ID).orderId());
        assertEquals(ORDER_ID, orderService.getOrder(ownStaff, ORDER_ID).orderId());
        assertEquals(ORDER_ID, orderService.getOrder(courier, ORDER_ID).orderId());
    }

    @Test
    void orderDetailsAreNotFoundForAnotherCustomerAndAnotherBakery() {
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(otherCustomer, ORDER_ID));
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(otherStaff, ORDER_ID));
    }

    @Test
    void bakeryIsLookedUpOnlyForStaffWhenOpeningAnOrder() {
        orderService.getOrder(operator, ORDER_ID);
        orderService.getOrder(ownCustomer, ORDER_ID);
        orderService.getOrder(courier, ORDER_ID);

        verify(bakeryStaffAccess, never()).getBakeryIdForSession(any());
    }

    @Test
    void detailsOfAMissingOrderAreNotFound() {
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder(ownStaff, MISSING_ORDER_ID));
    }

    // OrderServiceImpl.cancelOrder: only the customer's ownership is checked here; the State
    // classes decide what other roles may do

    @Test
    void theOwnCustomerCancelsTheirOrder() {
        assertEquals("cancelled", orderService.cancelOrder(ownCustomer, ORDER_ID).newState());
    }

    @Test
    void theOperatorCancelsAnyOrder() {
        assertEquals("cancelled", orderService.cancelOrder(operator, ORDER_ID).newState());
    }

    @Test
    void anotherCustomersCancelIsNotFound() {
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(otherCustomer, ORDER_ID));
    }

    @Test
    void staffCancelIsRefusedByTheOrderStateNotByVisibility() {
        assertThrows(IllegalTransitionException.class, () -> orderService.cancelOrder(otherStaff, ORDER_ID));
        assertThrows(IllegalTransitionException.class, () -> orderService.cancelOrder(courier, ORDER_ID));

        verify(bakeryStaffAccess, never()).getBakeryIdForSession(any());
    }

    // OrderSummaryService.getSummary: customers only, checked before the order is loaded

    @Test
    void summaryRefusesEveryOtherRoleBeforeLoadingTheOrder() {
        for (String session : List.of(operator, ownStaff, courier)) {
            SecurityException refused = assertThrows(SecurityException.class,
                    () -> summaryService.getSummary(session, MISSING_ORDER_ID));
            assertEquals("Only customers can view order summaries", refused.getMessage());
        }
        verify(orderRegistry, never()).findById(MISSING_ORDER_ID);
    }

    @Test
    void summaryOfAnotherCustomersOrderIsNotFound() {
        assertThrows(OrderNotFoundException.class, () -> summaryService.getSummary(otherCustomer, ORDER_ID));
    }

    @Test
    void summaryOfAMissingOrderIsNotFoundForACustomer() {
        assertThrows(OrderNotFoundException.class, () -> summaryService.getSummary(ownCustomer, MISSING_ORDER_ID));
    }

    @Test
    void theOwnCustomerPassesVisibilityAndReachesTheDeliveryCheck() {
        IllegalStateException notDelivered = assertThrows(IllegalStateException.class,
                () -> summaryService.getSummary(ownCustomer, ORDER_ID));

        assertEquals("Order summary is available only after delivery", notDelivered.getMessage());
    }

    private void givenPlacedOrder() {
        Order order = new Order("customer-1", OWN_BAKERY, "Alice", "123 Main Street");
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
    }
}
