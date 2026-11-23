package com.commitcrew.thebakeway.application.rvw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.ord.BakeryStaffAccessService;
import com.commitcrew.thebakeway.application.ord.OrderAccessPolicy;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierDelivery;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.rating.BakeryRating;
import com.commitcrew.thebakeway.domain.rating.BakeryRatingRepository;
import com.commitcrew.thebakeway.domain.rating.CourierRating;
import com.commitcrew.thebakeway.domain.rating.CourierRatingRepository;

// UC-RVW-01: rating the bakery and the courier of a delivered order in one submission
class OrderRatingServiceTest {

    private static final String ORDER_ID = "order-1";
    private static final String BAKERY_ID = "bakery-1";
    private static final Instant RATED_AT = Instant.parse("2026-11-18T20:00:00Z");

    private final SessionManager sessionManager = new SessionManager();
    private final OrderRegistry orderRegistry = mock(OrderRegistry.class);
    private final BakeryRatingRepository bakeryRatings = mock(BakeryRatingRepository.class);
    private final CourierRatingRepository courierRatings = mock(CourierRatingRepository.class);
    private final CourierRatingService courierRatingService = mock(CourierRatingService.class);

    private OrderRatingService service;
    private String customer;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderRatingService(sessionManager, new PermissionPolicy(), orderRegistry,
                new OrderAccessPolicy(mock(BakeryStaffAccessService.class)), bakeryRatings, courierRatings,
                courierRatingService);
        customer = sessionManager.createSession("customer-1", Role.CUSTOMER).getSessionId();
        order = new Order("customer-1", BAKERY_ID, "Alice", "123 Main Street");
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(bakeryRatings.saveAndFlush(any(BakeryRating.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courierRatingService.rateCourier(anyString(), anyString(), anyInt())).thenAnswer(invocation ->
                new CourierRatingView("courier-rating-1", ORDER_ID, "courier-1", invocation.getArgument(2), RATED_AT));
    }

    @Test
    void ratesTheBakeryAndTheCourierTogether() {
        deliver(order);

        OrderRatingView result = service.rateOrder(customer, ORDER_ID, 4, 5);

        ArgumentCaptor<BakeryRating> saved = ArgumentCaptor.forClass(BakeryRating.class);
        verify(bakeryRatings).saveAndFlush(saved.capture());
        assertEquals(4, saved.getValue().getScore());
        assertEquals(BAKERY_ID, saved.getValue().getBakeryId());
        assertEquals("customer-1", saved.getValue().getCustomerAccountId());
        verify(courierRatingService).rateCourier(customer, ORDER_ID, 5);
        assertEquals(new OrderRatingView(ORDER_ID, BAKERY_ID, 4, "courier-1", 5, result.ratedAt()), result);
    }

    @Test
    void onlyCustomersMayRate() {
        deliver(order);
        for (Role role : List.of(Role.OPERATOR, Role.RESTAURANT, Role.COURIER)) {
            String session = sessionManager.createSession("account-" + role, role).getSessionId();
            assertThrows(SecurityException.class, () -> service.rateOrder(session, ORDER_ID, 4, 5));
        }
        assertNothingSaved();
    }

    @Test
    void requiresASession() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> service.rateOrder("no-session", ORDER_ID, 4, 5));

        assertEquals("Authentication required", refused.getMessage());
        assertNothingSaved();
    }

    @Test
    void anotherCustomersOrderIsNotFound() {
        deliver(order);
        String otherCustomer = sessionManager.createSession("customer-2", Role.CUSTOMER).getSessionId();

        assertThrows(OrderNotFoundException.class, () -> service.rateOrder(otherCustomer, ORDER_ID, 4, 5));

        assertNothingSaved();
    }

    @Test
    void aMissingOrderIsNotFound() {
        when(orderRegistry.findById("missing")).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> service.rateOrder(customer, "missing", 4, 5));

        assertNothingSaved();
    }

    @Test
    void onlyDeliveredOrdersCanBeRated() {
        order.accept();
        order.startPreparing();
        order.markReady();
        order.confirmPickup();

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> service.rateOrder(customer, ORDER_ID, 4, 5));

        assertEquals("Only delivered orders can be rated", refused.getMessage());
        assertNothingSaved();
    }

    @Test
    void anOrderIsRatedOnlyOnce() {
        deliver(order);
        when(bakeryRatings.existsByOrder_Id(ORDER_ID)).thenReturn(true);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> service.rateOrder(customer, ORDER_ID, 4, 5));

        assertEquals("This order has already been rated", refused.getMessage());
        assertNothingSaved();
    }

    @Test
    void anOrderWithACourierRatingCountsAsRated() {
        deliver(order);
        when(courierRatings.existsByDelivery_Order_Id(ORDER_ID)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.rateOrder(customer, ORDER_ID, 4, 5));

        assertNothingSaved();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL, 5, The bakery rating must be from 1 to 5",
            "0, 5, The bakery rating must be from 1 to 5",
            "6, 5, The bakery rating must be from 1 to 5",
            "4, NULL, The courier rating must be from 1 to 5",
            "4, 0, The courier rating must be from 1 to 5",
            "4, 6, The courier rating must be from 1 to 5"},
            nullValues = "NULL")
    void bothRatingsAreRequiredFromOneToFive(Integer bakeryScore, Integer courierScore, String message) {
        deliver(order);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> service.rateOrder(customer, ORDER_ID, bakeryScore, courierScore));

        assertEquals(message, refused.getMessage());
        assertNothingSaved();
    }

    @Test
    void aConcurrentDuplicateIsReportedAsAlreadyRated() {
        deliver(order);
        when(bakeryRatings.saveAndFlush(any(BakeryRating.class)))
                .thenThrow(new DataIntegrityViolationException("uk_bakery_ratings_order"));

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> service.rateOrder(customer, ORDER_ID, 4, 5));

        assertEquals("This order has already been rated", refused.getMessage());
        verify(courierRatingService, never()).rateCourier(anyString(), anyString(), anyInt());
    }

    @Test
    void aCourierRatingFailureFailsTheWholeSubmission() {
        deliver(order);
        when(courierRatingService.rateCourier(customer, ORDER_ID, 5))
                .thenThrow(new IllegalStateException("Delivered order has no recorded courier"));

        // The exception leaves the transaction, so the bakery rating saved before it is rolled back
        assertThrows(IllegalStateException.class, () -> service.rateOrder(customer, ORDER_ID, 4, 5));
    }

    @Test
    void listsTheCustomersRatedOrdersWithBothScores() {
        deliver(order);
        BakeryRating bakeryRating = new BakeryRating(order, "customer-1", 4, RATED_AT);
        when(bakeryRatings.findByCustomerAccountIdOrderByRatedAtDesc("customer-1")).thenReturn(List.of(bakeryRating));
        Courier courier = new Courier("courier-1");
        CourierDelivery delivery = mock(CourierDelivery.class);
        when(delivery.getOrder()).thenReturn(order);
        when(delivery.getCourier()).thenReturn(courier);
        when(courierRatings.findByCustomerAccountIdOrderByRatedAtDesc("customer-1"))
                .thenReturn(List.of(new CourierRating(delivery, "customer-1", 5, RATED_AT)));

        assertEquals(List.of(new OrderRatingView(ORDER_ID, BAKERY_ID, 4, "courier-1", 5, RATED_AT)),
                service.getMyRatings(customer));
    }

    @Test
    void calculatesAndRoundsBakeryAverageRating() {
        BakeryRating first = mock(BakeryRating.class);
        BakeryRating second = mock(BakeryRating.class);
        BakeryRating third = mock(BakeryRating.class);
        when(first.getScore()).thenReturn(4);
        when(second.getScore()).thenReturn(5);
        when(third.getScore()).thenReturn(5);
        when(bakeryRatings.findByBakeryId(BAKERY_ID))
                .thenReturn(List.of(first, second, third));

        assertEquals(4.7, service.getBakeryAvgRating(customer, BAKERY_ID), 0.000001);
        verify(bakeryRatings).findByBakeryId(BAKERY_ID);
    }

    @Test
    void returnsZeroAverageWhenBakeryHasNoRatings() {
        when(bakeryRatings.findByBakeryId(BAKERY_ID)).thenReturn(List.of());
        
        assertEquals(0.0, service.getBakeryAvgRating(customer, BAKERY_ID), 0.000001);
        verify(bakeryRatings).findByBakeryId(BAKERY_ID);
    }

    @Test
    void bakeryAverageRatingRequiresLoggedInSession() {
        assertThrows(IllegalStateException.class,
                () -> service.getBakeryAvgRating("no-session", BAKERY_ID));
        verify(bakeryRatings, never()).findByBakeryId(anyString());
    }

    private void deliver(Order order) {
        order.accept();
        order.startPreparing();
        order.markReady();
        order.confirmPickup();
        order.confirmDelivery();
    }

    private void assertNothingSaved() {
        verify(bakeryRatings, never()).saveAndFlush(any());
        verify(courierRatingService, never()).rateCourier(anyString(), anyString(), anyInt());
    }
}
