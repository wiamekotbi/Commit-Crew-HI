package com.commitcrew.thebakeway.application.rvw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierDelivery;
import com.commitcrew.thebakeway.domain.courier.CourierDeliveryRepository;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.rating.CourierRating;
import com.commitcrew.thebakeway.domain.rating.CourierRatingRepository;
import com.commitcrew.thebakeway.domain.session.Session;

@ExtendWith(MockitoExtension.class)
class CourierRatingServiceTest {

    private static final String SESSION_ID = "session-1";
    private static final String CUSTOMER_ID = "customer-1";
    private static final String ORDER_ID = "order-1";

    @Mock private SessionManager sessionManager;
    @Mock private OrderRegistry orderRegistry;
    @Mock private CourierDeliveryRepository deliveryRepository;
    @Mock private CourierRatingRepository ratingRepository;
    @Mock private Order order;
    @Mock private CourierDelivery delivery;
    @Mock private Courier courier;

    private CourierRatingService service;

    @BeforeEach
    void setUp() {
        service = new CourierRatingService(
                sessionManager, orderRegistry, deliveryRepository, ratingRepository);
    }

    @Test
    void customerRatesCourierWhoDeliveredOwnOrder() {
        givenCustomer();
        givenDeliveredOwnOrder();
        when(order.getId()).thenReturn(ORDER_ID);
        when(ratingRepository.existsByDelivery_Order_Id(ORDER_ID)).thenReturn(false);
        when(deliveryRepository.findByOrder_Id(ORDER_ID)).thenReturn(Optional.of(delivery));
        when(delivery.getOrder()).thenReturn(order);
        when(delivery.getCourier()).thenReturn(courier);
        when(courier.getCourierId()).thenReturn("courier-1");
        when(ratingRepository.saveAndFlush(any(CourierRating.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourierRatingView result = service.rateCourier(SESSION_ID, ORDER_ID, 5);

        assertEquals(ORDER_ID, result.orderId());
        assertEquals("courier-1", result.courierId());
        assertEquals(5, result.score());
        verify(ratingRepository).saveAndFlush(any(CourierRating.class));
    }

    @Test
    void refusesDuplicateRating() {
        givenCustomer();
        givenDeliveredOwnOrder();
        when(ratingRepository.existsByDelivery_Order_Id(ORDER_ID)).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.rateCourier(SESSION_ID, ORDER_ID, 4));

        assertEquals("The courier for this order has already been rated", error.getMessage());
        verify(ratingRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesRatingAnotherCustomersOrder() {
        givenCustomer();
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(order.getCustomerId()).thenReturn("customer-2");

        SecurityException error = assertThrows(SecurityException.class,
                () -> service.rateCourier(SESSION_ID, ORDER_ID, 5));

        assertEquals("Customers can rate only couriers who delivered their own orders", error.getMessage());
    }

    @Test
    void refusesRatingBeforeDelivery() {
        givenCustomer();
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(order.getCustomerId()).thenReturn(CUSTOMER_ID);
        when(order.getStatus()).thenReturn("picked_up");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.rateCourier(SESSION_ID, ORDER_ID, 5));

        assertEquals("Courier ratings are allowed only for delivered orders", error.getMessage());
    }

    @Test
    void refusesScoreOutsideOneToFive() {
        givenCustomer();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.rateCourier(SESSION_ID, ORDER_ID, 6));

        assertEquals("Courier rating must be between 1 and 5", error.getMessage());
        verify(orderRegistry, never()).findById(any());
    }

    @Test
    void refusesNonCustomer() {
        when(sessionManager.findSession(SESSION_ID))
                .thenReturn(Optional.of(new Session(SESSION_ID, "operator-1", Role.OPERATOR)));

        SecurityException error = assertThrows(SecurityException.class,
                () -> service.rateCourier(SESSION_ID, ORDER_ID, 5));

        assertEquals("Only customers can rate couriers", error.getMessage());
    }

    @Test
    void refusesDeliveredOrderWithoutRecordedCourier() {
        givenCustomer();
        givenDeliveredOwnOrder();
        when(ratingRepository.existsByDelivery_Order_Id(ORDER_ID)).thenReturn(false);
        when(deliveryRepository.findByOrder_Id(ORDER_ID)).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.rateCourier(SESSION_ID, ORDER_ID, 5));

        assertEquals("Delivered order has no recorded courier", error.getMessage());
    }

    private void givenCustomer() {
        when(sessionManager.findSession(SESSION_ID))
                .thenReturn(Optional.of(new Session(SESSION_ID, CUSTOMER_ID, Role.CUSTOMER)));
    }

    private void givenDeliveredOwnOrder() {
        when(orderRegistry.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(order.getCustomerId()).thenReturn(CUSTOMER_ID);
        when(order.getStatus()).thenReturn("delivered");
    }
}
