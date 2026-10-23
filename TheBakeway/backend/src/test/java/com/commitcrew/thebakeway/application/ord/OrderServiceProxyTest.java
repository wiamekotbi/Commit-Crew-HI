package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.session.Session;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

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

        OrderTransitionResult placed = new OrderTransitionResult(ORDER_ID, null, "placed", "event-1", null);
        when(realSubject.placeOrder(SESSION_ID, REQUEST)).thenReturn(placed);

        OrderTransitionResult result = orderServiceProxy.placeOrder(SESSION_ID, REQUEST);

        assertEquals(placed, result);
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

    @Test
    void delegatesOpenBakeriesWhenCustomerIsAuthorized() {
        // Arrange a valid customer session with permission to browse.
        Session session = new Session(SESSION_ID, "account-1", Role.CUSTOMER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.BROWSE_MENU, Role.CUSTOMER)).thenReturn(true);

        Bakery bakery = new Bakery("bakery-1", "Sweet Treats", BakeryStatus.open, "123 Main St");
        List<Bakery> expectedBakeries = List.of(bakery);
        when(realSubject.getOpenBakeries(SESSION_ID)).thenReturn(expectedBakeries);

        List<Bakery> actualBakeries = orderServiceProxy.getOpenBakeries(SESSION_ID);

        // The proxy forwards the authorized request and preserves the returned data.
        assertEquals(expectedBakeries, actualBakeries);
        verify(permissionPolicy).isAllowed(OrderServiceProxy.BROWSE_MENU, Role.CUSTOMER);
        verify(realSubject).getOpenBakeries(SESSION_ID);
    }

    @Test 
    void delegatesMenuBrowsingWhenCustomerIsAuthorized(){
        // Arrange a valid customer session with permission to browse.
        Session session = new Session(SESSION_ID, "account-1", Role.CUSTOMER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.BROWSE_MENU, Role.CUSTOMER)).thenReturn(true);

        MenuItem item1 = new MenuItem("bakery-1", "Croissant", "Butter croissant", 3.50,
                MenuItemStatus.available);
        MenuItem item2 = new MenuItem("bakery-1", "Baguette", "French baguette", 4.00,
                MenuItemStatus.sold_out);
        List<MenuItem> expectedMenu = List.of(item1, item2);
        when(realSubject.getMenuForBrowsing(SESSION_ID, "bakery-1")).thenReturn(expectedMenu);

        List<MenuItem> actualMenu = orderServiceProxy.getMenuForBrowsing(SESSION_ID, "bakery-1");

        // The proxy forwards the authorized request and preserves the returned data.
        assertEquals(expectedMenu, actualMenu);
        verify(permissionPolicy).isAllowed(OrderServiceProxy.BROWSE_MENU, Role.CUSTOMER);
        verify(realSubject).getMenuForBrowsing(SESSION_ID, "bakery-1");
    }

    @Test 
    void rejectsOpenBakeriesWithoutValidSession() {
        // Arrange: no session found for the given ID.
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.empty());

        // Act & Assert: the proxy should throw an exception for missing session.
        assertThrows(IllegalStateException.class,
                () -> orderServiceProxy.getOpenBakeries(SESSION_ID));

        // Session rejection must stop both permission checking and all delegation.
        verifyNoInteractions(realSubject, permissionPolicy);
    }   

    @Test 
    void rejectsMenuBrowsingWithoutValidSession() {
        // Arrange: no session found for the given ID.
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.empty());
        // Act & Assert: the proxy should throw an exception for missing session.
        assertThrows(IllegalStateException.class,
                () -> orderServiceProxy.getMenuForBrowsing(SESSION_ID, "bakery-1"));    
    
        // No protected operation should run when the session is invalid.
        verifyNoInteractions(realSubject, permissionPolicy);
    }

    @Test 
    void rejectsOpenBakeriesWithoutBrowsingPermission(){
        // Arrange: a valid session but without permission to browse.
        Session session = new Session(SESSION_ID, "account-1", Role.COURIER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.BROWSE_MENU, Role.COURIER)).thenReturn(false);

        // Act & Assert: the proxy should throw an exception for unauthorized access.
        assertThrows(SecurityException.class,
                () -> orderServiceProxy.getOpenBakeries(SESSION_ID));

        // No delegation should occur when the permission check fails.
        verify(permissionPolicy).isAllowed(OrderServiceProxy.BROWSE_MENU, Role.COURIER);
        verifyNoInteractions(realSubject);
    }

    @Test 
    void rejectsMenuBrowsingWithoutBrowsingPermission(){
        // Arrange: a valid session but without permission to browse.
        Session session = new Session(SESSION_ID, "account-1", Role.COURIER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.BROWSE_MENU, Role.COURIER)).thenReturn(false);  
        
        // Act & Assert: the proxy should throw an exception for unauthorized access.
        assertThrows(SecurityException.class,
                () -> orderServiceProxy.getMenuForBrowsing(SESSION_ID, "bakery-1"));
    
        // No delegation should occur when the permission check fails.
        verify(permissionPolicy).isAllowed(OrderServiceProxy.BROWSE_MENU, Role.COURIER);
        verifyNoInteractions(realSubject);  
    }

    @Test
    void delegatesMenuManagementWhenRestaurantIsAuthorized() {
        Session session = new Session(SESSION_ID, "staff-1", Role.RESTAURANT);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.MANAGE_MENU_ITEMS, Role.RESTAURANT))
                .thenReturn(true);
        MenuItem item = new MenuItem(
                "bakery-1", "Croissant", "Butter croissant", 3.50,
                MenuItemStatus.available);
        when(realSubject.addMenuItem(
                SESSION_ID, "Croissant", "Butter croissant", 3.50)).thenReturn(item);

        MenuItem actual = orderServiceProxy.addMenuItem(
                SESSION_ID, "Croissant", "Butter croissant", 3.50);

        assertEquals(item, actual);
        verify(realSubject).addMenuItem(
                SESSION_ID, "Croissant", "Butter croissant", 3.50);
    }

    @Test
    void rejectsMenuManagementWithoutPermission() {
        Session session = new Session(SESSION_ID, "customer-1", Role.CUSTOMER);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.MANAGE_MENU_ITEMS, Role.CUSTOMER))
                .thenReturn(false);

        assertThrows(SecurityException.class, () -> orderServiceProxy.editMenuItem(
                SESSION_ID, "item-1", "Changed", "Changed", 4.00));

        verify(realSubject, never()).editMenuItem(
                SESSION_ID, "item-1", "Changed", "Changed", 4.00);
    }

    @Test
    void delegatesAvailabilityChangeWhenRestaurantIsAuthorized() {
        Session session = new Session(SESSION_ID, "staff-1", Role.RESTAURANT);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.MANAGE_MENU_ITEMS, Role.RESTAURANT))
                .thenReturn(true);
        MenuItem expected = new MenuItem(
                "bakery-1", "Croissant", "Butter croissant", 3.50,
                MenuItemStatus.sold_out);
        when(realSubject.updateMenuItemAvailability(
                SESSION_ID, "item-1", MenuItemStatus.sold_out)).thenReturn(expected);

        MenuItem actual = orderServiceProxy.updateMenuItemAvailability(
                SESSION_ID, "item-1", MenuItemStatus.sold_out);

        assertEquals(expected, actual);
        verify(permissionPolicy).isAllowed(
                OrderServiceProxy.MANAGE_MENU_ITEMS, Role.RESTAURANT);
        verify(realSubject).updateMenuItemAvailability(
                SESSION_ID, "item-1", MenuItemStatus.sold_out);
    }

    @Test
    void delegatesPauseWhenRestaurantHasPausePermission() {
        Session session = new Session(SESSION_ID, "staff-1", Role.RESTAURANT);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.PAUSE_BAKERY, Role.RESTAURANT))
                .thenReturn(true);
        Bakery paused = new Bakery(
                "bakery-1", "Sweet Treats", BakeryStatus.paused, "123 Main St");
        when(realSubject.pauseBakery(SESSION_ID, "bakery-1")).thenReturn(paused);

        assertEquals(paused, orderServiceProxy.pauseBakery(SESSION_ID, "bakery-1"));

        verify(permissionPolicy).isAllowed(OrderServiceProxy.PAUSE_BAKERY, Role.RESTAURANT);
        verify(realSubject).pauseBakery(SESSION_ID, "bakery-1");
    }

    @Test
    void rejectsReopenWhenRestaurantLacksOperatorPermission() {
        Session session = new Session(SESSION_ID, "staff-1", Role.RESTAURANT);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.REOPEN_BAKERY, Role.RESTAURANT))
                .thenReturn(false);

        assertThrows(SecurityException.class,
                () -> orderServiceProxy.reopenBakery(SESSION_ID, "bakery-1"));

        verify(realSubject, never()).reopenBakery(SESSION_ID, "bakery-1");
    }

    @Test
    void delegatesReopenWhenOperatorIsAuthorized() {
        Session session = new Session(SESSION_ID, "operator-1", Role.OPERATOR);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.REOPEN_BAKERY, Role.OPERATOR))
                .thenReturn(true);
        Bakery open = new Bakery(
                "bakery-1", "Sweet Treats", BakeryStatus.open, "123 Main St");
        when(realSubject.reopenBakery(SESSION_ID, "bakery-1")).thenReturn(open);

        assertEquals(open, orderServiceProxy.reopenBakery(SESSION_ID, "bakery-1"));

        verify(permissionPolicy).isAllowed(OrderServiceProxy.REOPEN_BAKERY, Role.OPERATOR);
        verify(realSubject).reopenBakery(SESSION_ID, "bakery-1");
    }

    @Test
    void rejectsRestoreWhenUserIsNotOperator() {
        Session session = new Session(SESSION_ID, "staff-1", Role.RESTAURANT);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.RESTORE_INITIAL_STATE, Role.RESTAURANT))
                .thenReturn(false);

        assertThrows(SecurityException.class, () -> orderServiceProxy.restoreInitialState(SESSION_ID));

        verify(realSubject, never()).restoreInitialState(SESSION_ID);
    }

    @Test
    void delegatesRestoreWhenOperatorIsAuthorized() {
        Session session = new Session(SESSION_ID, "operator-1", Role.OPERATOR);
        when(sessionManager.findSession(SESSION_ID)).thenReturn(Optional.of(session));
        when(permissionPolicy.isAllowed(OrderServiceProxy.RESTORE_INITIAL_STATE, Role.OPERATOR))
                .thenReturn(true);

        orderServiceProxy.restoreInitialState(SESSION_ID);

        verify(realSubject).restoreInitialState(SESSION_ID);
    }

    @Test
    void readMethodsCheckTheirPermission() {
        givenRole(Role.OPERATOR, OrderServiceProxy.VIEW_SIMULATOR_DASHBOARD, true);
        orderServiceProxy.listAllOrders(SESSION_ID, null, null);
        verify(realSubject).listAllOrders(SESSION_ID, null, null);

        givenRole(Role.CUSTOMER, OrderServiceProxy.VIEW_ORDER_HISTORY, true);
        orderServiceProxy.listOrdersForCustomer(SESSION_ID);
        verify(realSubject).listOrdersForCustomer(SESSION_ID);

        givenRole(Role.RESTAURANT, OrderServiceProxy.MANAGE_ORDERS, true);
        orderServiceProxy.listOrdersForBakery(SESSION_ID);
        verify(realSubject).listOrdersForBakery(SESSION_ID);

        givenRole(Role.CUSTOMER, OrderServiceProxy.VIEW_ORDER_DETAILS, true);
        orderServiceProxy.getOrder(SESSION_ID, ORDER_ID);
        verify(realSubject).getOrder(SESSION_ID, ORDER_ID);
    }

    @Test
    void rejectsAllOrdersListForNonOperator() {
        givenRole(Role.CUSTOMER, OrderServiceProxy.VIEW_SIMULATOR_DASHBOARD, false);

        assertThrows(SecurityException.class, () -> orderServiceProxy.listAllOrders(SESSION_ID, null, null));

        verify(realSubject, never()).listAllOrders(SESSION_ID, null, null);
    }

    private void givenRole(Role role, String operation, boolean allowed) {
        when(sessionManager.findSession(SESSION_ID))
                .thenReturn(Optional.of(new Session(SESSION_ID, "account-1", role)));
        when(permissionPolicy.isAllowed(operation, role)).thenReturn(allowed);
    }

    @Test
    void listsAllBakeriesOnlyForTheOperator() {
        givenRole(Role.CUSTOMER, OrderServiceProxy.VIEW_SIMULATOR_DASHBOARD, false);

        assertThrows(SecurityException.class, () -> orderServiceProxy.listAllBakeries(SESSION_ID));

        verify(realSubject, never()).listAllBakeries(SESSION_ID);
    }

    @Test
    void listsOwnMenuOnlyWithMenuPermission() {
        givenRole(Role.CUSTOMER, OrderServiceProxy.MANAGE_MENU_ITEMS, false);

        assertThrows(SecurityException.class, () -> orderServiceProxy.listOwnMenuItems(SESSION_ID));

        verify(realSubject, never()).listOwnMenuItems(SESSION_ID);
    }
}
