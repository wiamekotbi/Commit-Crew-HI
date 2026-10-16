package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

@ExtendWith(MockitoExtension.class)
class OrderServiceProxyTest {

    private static final String SESSION_ID = "session-1";
    private static final String ORDER_ID = "order-1";
    private static final PlaceOrderRequest REQUEST = new PlaceOrderRequest(
            "bakery-1", List.of(new OrderItemRequest("item-1", 1)), "Montreal");

    @Mock
    private OrderService realSubject;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private PermissionPolicy permissionPolicy;

    private OrderServiceProxy orderServiceProxy;

    @BeforeEach
    void setUp() {
        orderServiceProxy = new OrderServiceProxy(realSubject, sessionManager, permissionPolicy);
    }

    @Test
    void rejectsMissingSession() {
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> orderServiceProxy.placeOrder(SESSION_ID, REQUEST));

        verify(realSubject, never()).placeOrder(SESSION_ID, REQUEST);
    }

    @Test
    void rejectsUnauthorizedRole() {
        Session session = new Session(SESSION_ID, "account-1", Role.COURIER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.PLACE_ORDER, Role.COURIER)).thenReturn(false);

        assertThrows(SecurityException.class,
                () -> orderServiceProxy.placeOrder(SESSION_ID, REQUEST));

        verify(realSubject, never()).placeOrder(SESSION_ID, REQUEST);
    }

    @Test
    void delegatesPlaceOrderWhenCustomerIsAuthorized() {
        Session session = new Session(SESSION_ID, "account-1", Role.CUSTOMER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.PLACE_ORDER, Role.CUSTOMER)).thenReturn(true);

        when(realSubject.placeOrder(SESSION_ID, REQUEST)).thenReturn(ORDER_ID);

        String orderId = orderServiceProxy.placeOrder(SESSION_ID, REQUEST);

        assertEquals(ORDER_ID, orderId);
        verify(realSubject).placeOrder(SESSION_ID, REQUEST);
    }

    @Test
    void delegatesRestaurantOperationsWhenAuthorized() {
        Session session = new Session(SESSION_ID, "restaurant-1", Role.RESTAURANT);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.MANAGE_ORDERS, Role.RESTAURANT)).thenReturn(true);
        when(permissionPolicy.isAllowed(OrderServiceProxy.PREPARE_ORDERS, Role.RESTAURANT)).thenReturn(true);

        orderServiceProxy.acceptOrder(SESSION_ID, ORDER_ID);
        orderServiceProxy.startPreparing(SESSION_ID, ORDER_ID);

        verify(realSubject).acceptOrder(SESSION_ID, ORDER_ID);
        verify(realSubject).startPreparing(SESSION_ID, ORDER_ID);
    }

    @Test
    void delegatesRejectWithReasonWhenAuthorized() {
        Session session = new Session(SESSION_ID, "restaurant-1", Role.RESTAURANT);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.MANAGE_ORDERS, Role.RESTAURANT)).thenReturn(true);

        orderServiceProxy.rejectOrder(SESSION_ID, ORDER_ID, "Out of flour");

        verify(realSubject).rejectOrder(SESSION_ID, ORDER_ID, "Out of flour");
    }

    @Test
    void delegatesCancelWhenOperatorIsAuthorized() {
        Session session = new Session(SESSION_ID, "operator-1", Role.OPERATOR);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.CANCEL_ORDER, Role.OPERATOR)).thenReturn(true);

        orderServiceProxy.cancelOrder(SESSION_ID, ORDER_ID);

        verify(realSubject).cancelOrder(SESSION_ID, ORDER_ID);
    }

    @Test
    void rejectsCancelWithoutCancelPermission() {
        Session session = new Session(SESSION_ID, "restaurant-1", Role.RESTAURANT);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.CANCEL_ORDER, Role.RESTAURANT)).thenReturn(false);

        assertThrows(SecurityException.class,
                () -> orderServiceProxy.cancelOrder(SESSION_ID, ORDER_ID));

        verify(realSubject, never()).cancelOrder(SESSION_ID, ORDER_ID);
    }

    @Test
    void checkAccessReturnsSessionForAllowedOperation() {
        Session session = new Session(SESSION_ID, "account-1", Role.CUSTOMER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.PLACE_ORDER, Role.CUSTOMER)).thenReturn(true);

        Session result = orderServiceProxy.checkAccess(SESSION_ID, OrderServiceProxy.PLACE_ORDER);

        assertEquals(session, result);
    }
}
