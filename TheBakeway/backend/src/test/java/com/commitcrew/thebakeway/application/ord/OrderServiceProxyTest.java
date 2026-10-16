package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                () -> orderServiceProxy.placeOrder(SESSION_ID));

        verify(realSubject, never()).placeOrder(SESSION_ID);
    }

    @Test
    void rejectsUnauthorizedRole() {
        Session session = new Session(SESSION_ID, "account-1", Role.COURIER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.PLACE_ORDER, Role.COURIER)).thenReturn(false);

        assertThrows(SecurityException.class,
                () -> orderServiceProxy.placeOrder(SESSION_ID));

        verify(realSubject, never()).placeOrder(SESSION_ID);
    }

    @Test
    void delegatesPlaceOrderWhenCustomerIsAuthorized() {
        Session session = new Session(SESSION_ID, "account-1", Role.CUSTOMER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.PLACE_ORDER, Role.CUSTOMER)).thenReturn(true);

        orderServiceProxy.placeOrder(SESSION_ID);

        verify(realSubject).placeOrder(SESSION_ID);
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
    void checkAccessReturnsSessionForAllowedOperation() {
        Session session = new Session(SESSION_ID, "account-1", Role.CUSTOMER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.PLACE_ORDER, Role.CUSTOMER)).thenReturn(true);

        Session result = orderServiceProxy.checkAccess(SESSION_ID, OrderServiceProxy.PLACE_ORDER);

        assertEquals(session, result);
    }
}
