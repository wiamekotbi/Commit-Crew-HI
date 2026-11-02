package com.commitcrew.thebakeway.application.ord;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.session.Session;

import java.util.List;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

@Service
@Primary
public class OrderServiceProxy implements OrderService {

    static final String PLACE_ORDER = "PLACE_ORDER";
    static final String CANCEL_ORDER = "CANCEL_ORDER";
    static final String MANAGE_ORDERS = "MANAGE_ORDERS";
    static final String PREPARE_ORDERS = "PREPARE_ORDERS";
    static final String CONFIRM_PICKUP = "CONFIRM_PICKUP";
    static final String CONFIRM_DELIVERY = "CONFIRM_DELIVERY";
    static final String BROWSE_MENU = "BROWSE_MENU";
    static final String MANAGE_MENU_ITEMS = "MANAGE_MENU_ITEMS";
    static final String PAUSE_BAKERY = "PAUSE_BAKERY";
    static final String REOPEN_BAKERY = "REOPEN_BAKERY";
    static final String RESTORE_INITIAL_STATE = "RESTORE_INITIAL_STATE";
    static final String VIEW_ORDER_HISTORY = "VIEW_ORDER_HISTORY";
    static final String VIEW_SIMULATOR_DASHBOARD = "VIEW_SIMULATOR_DASHBOARD";
    static final String VIEW_ORDER_DETAILS = "VIEW_ORDER_DETAILS";

    private final OrderService realSubject;
    private final SessionManager sessionManager;
    private final PermissionPolicy permissionPolicy;

    public OrderServiceProxy(
            @Qualifier("orderServiceImpl") OrderService realSubject,
            SessionManager sessionManager,
            PermissionPolicy permissionPolicy) {
        this.realSubject = realSubject;
        this.sessionManager = sessionManager;
        this.permissionPolicy = permissionPolicy;
    }

    public Session checkAccess(String sessionId, String operation) {
        Session session = sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));

        if (!permissionPolicy.isAllowed(operation, session.getRole())) {
            throw new SecurityException("Access denied for operation: " + operation);
        }

        return session;
    }

    @Override
    public OrderTransitionResult placeOrder(String sessionId, PlaceOrderRequest request) {
        checkAccess(sessionId, PLACE_ORDER);
        return realSubject.placeOrder(sessionId, request);
    }

    @Override
    public OrderTransitionResult cancelOrder(String sessionId, String orderId) {
        checkAccess(sessionId, CANCEL_ORDER);
        return realSubject.cancelOrder(sessionId, orderId);
    }

    @Override
    public OrderTransitionResult acceptOrder(String sessionId, String orderId) {
        checkAccess(sessionId, MANAGE_ORDERS);
        return realSubject.acceptOrder(sessionId, orderId);
    }

    @Override
    public OrderTransitionResult rejectOrder(String sessionId, String orderId, String reason) {
        checkAccess(sessionId, MANAGE_ORDERS);
        return realSubject.rejectOrder(sessionId, orderId, reason);
    }

    @Override
    public OrderTransitionResult startPreparing(String sessionId, String orderId) {
        checkAccess(sessionId, PREPARE_ORDERS);
        return realSubject.startPreparing(sessionId, orderId);
    }

    @Override
    public OrderTransitionResult markReady(String sessionId, String orderId) {
        checkAccess(sessionId, PREPARE_ORDERS);
        return realSubject.markReady(sessionId, orderId);
    }

    @Override
    public OrderTransitionResult confirmPickup(String sessionId, String orderId) {
        checkAccess(sessionId, CONFIRM_PICKUP);
        return realSubject.confirmPickup(sessionId, orderId);
    }

    @Override
    public OrderTransitionResult confirmDelivery(String sessionId, String orderId) {
        checkAccess(sessionId, CONFIRM_DELIVERY);
        return realSubject.confirmDelivery(sessionId, orderId);
    }

    @Override
    public List<OrderView> listOrdersForCustomer(String sessionId) {
        checkAccess(sessionId, VIEW_ORDER_HISTORY);
        return realSubject.listOrdersForCustomer(sessionId);
    }

    @Override
    public List<OrderView> listOrdersForBakery(String sessionId) {
        checkAccess(sessionId, MANAGE_ORDERS);
        return realSubject.listOrdersForBakery(sessionId);
    }

    @Override
    public List<OrderView> listAllOrders(String sessionId, String bakeryId, String status) {
        // Only the operator sees every bakery's orders (UC-ORD-13).
        checkAccess(sessionId, VIEW_SIMULATOR_DASHBOARD);
        return realSubject.listAllOrders(sessionId, bakeryId, status);
    }

    @Override
    public OrderView getOrder(String sessionId, String orderId) {
        // Customers, staff and the operator may open an order; ownership is checked in OrderServiceImpl.
        checkAccess(sessionId, VIEW_ORDER_DETAILS);
        return realSubject.getOrder(sessionId, orderId);
    }

    @Override
    public List<Bakery> getOpenBakeries(String sessionId) {
        checkAccess(sessionId, BROWSE_MENU);
        return realSubject.getOpenBakeries(sessionId);
    }

    @Override
    public List<Bakery> listAllBakeries(String sessionId) {
        // The operator sees every bakery, including paused and closed ones.
        checkAccess(sessionId, VIEW_SIMULATOR_DASHBOARD);
        return realSubject.listAllBakeries(sessionId);
    }

    @Override
    public List<MenuItem> getMenuForBrowsing(String sessionId, String bakeryId) {
        checkAccess(sessionId, BROWSE_MENU);
        return realSubject.getMenuForBrowsing(sessionId, bakeryId);
    }

    @Override
    public List<MenuItem> listOwnMenuItems(String sessionId) {
        // Staff see their own bakery's full menu, sold-out items included (UC-ORD-09 step 2).
        checkAccess(sessionId, MANAGE_MENU_ITEMS);
        return realSubject.listOwnMenuItems(sessionId);
    }

    @Override
    public MenuItem addMenuItem(String sessionId, String name, String description, double price) {
        checkAccess(sessionId, MANAGE_MENU_ITEMS);
        return realSubject.addMenuItem(sessionId, name, description, price);
    }

    @Override
    public MenuItem editMenuItem(
            String sessionId, String itemId, String name, String description, double price) {
        checkAccess(sessionId, MANAGE_MENU_ITEMS);
        return realSubject.editMenuItem(sessionId, itemId, name, description, price);
    }

    @Override
    public MenuItem updateMenuItemAvailability(
            String sessionId, String itemId, MenuItemStatus newStatus) {
        // Availability is a menu-management operation and therefore uses the
        // same restaurant-only permission enforced for add and edit commands.
        checkAccess(sessionId, MANAGE_MENU_ITEMS);
        return realSubject.updateMenuItemAvailability(sessionId, itemId, newStatus);
    }

    @Override
    public Bakery pauseBakery(String sessionId, String bakeryId) {
        // Both restaurant staff and operators may pass this role-level check;
        // the implementation still enforces bakery ownership for restaurant staff.
        checkAccess(sessionId, PAUSE_BAKERY);
        return realSubject.pauseBakery(sessionId, bakeryId);
    }

    @Override
    public Bakery reopenBakery(String sessionId, String bakeryId) {
        // PermissionPolicy grants this operation only to operators.
        checkAccess(sessionId, REOPEN_BAKERY);
        return realSubject.reopenBakery(sessionId, bakeryId);
    }

    @Override
    public InitialStateSummary restoreInitialState(String sessionId) {
        // Only the operator may restore the initial state (UC-REG-03, UC-ORD-01).
        checkAccess(sessionId, RESTORE_INITIAL_STATE);
        return realSubject.restoreInitialState(sessionId);
    }
}
