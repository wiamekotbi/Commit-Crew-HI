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
    static final String BROWSE_MENU = "BROWSE_MENU";
    static final String MANAGE_MENU_ITEMS = "MANAGE_MENU_ITEMS";

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
    public String placeOrder(String sessionId, PlaceOrderRequest request) {
        checkAccess(sessionId, PLACE_ORDER);
        return realSubject.placeOrder(sessionId, request);
    }

    @Override
    public void cancelOrder(String sessionId, String orderId) {
        checkAccess(sessionId, CANCEL_ORDER);
        realSubject.cancelOrder(sessionId, orderId);
    }

    @Override
    public void acceptOrder(String sessionId, String orderId) {
        checkAccess(sessionId, MANAGE_ORDERS);
        realSubject.acceptOrder(sessionId, orderId);
    }

    @Override
    public void rejectOrder(String sessionId, String orderId, String reason) {
        checkAccess(sessionId, MANAGE_ORDERS);
        realSubject.rejectOrder(sessionId, orderId, reason);
    }

    @Override
    public void startPreparing(String sessionId, String orderId) {
        checkAccess(sessionId, PREPARE_ORDERS);
        realSubject.startPreparing(sessionId, orderId);
    }

    @Override
    public void markReady(String sessionId, String orderId) {
        checkAccess(sessionId, PREPARE_ORDERS);
        realSubject.markReady(sessionId, orderId);
    }

    @Override
    public List<Bakery> getOpenBakeries(String sessionId) {
        checkAccess(sessionId, BROWSE_MENU);
        return realSubject.getOpenBakeries(sessionId);
    }

    @Override
    public List<MenuItem> getMenuForBrowsing(String sessionId, String bakeryId) {
        checkAccess(sessionId, BROWSE_MENU);
        return realSubject.getMenuForBrowsing(sessionId, bakeryId);
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
}
