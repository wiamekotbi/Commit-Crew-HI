package com.commitcrew.thebakeway.application.ord;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.session.Session;

@Service
@Primary
public class OrderServiceProxy implements OrderService {

    static final String PLACE_ORDER = "PLACE_ORDER";
    static final String MANAGE_ORDERS = "MANAGE_ORDERS";
    static final String PREPARE_ORDERS = "PREPARE_ORDERS";

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
    public void placeOrder(String sessionId) {
        checkAccess(sessionId, PLACE_ORDER);
        realSubject.placeOrder(sessionId);
    }

    @Override
    public void cancelOrder(String sessionId, String orderId) {
        checkAccess(sessionId, PLACE_ORDER);
        realSubject.cancelOrder(sessionId, orderId);
    }

    @Override
    public void acceptOrder(String sessionId, String orderId) {
        checkAccess(sessionId, MANAGE_ORDERS);
        realSubject.acceptOrder(sessionId, orderId);
    }

    @Override
    public void rejectOrder(String sessionId, String orderId) {
        checkAccess(sessionId, MANAGE_ORDERS);
        realSubject.rejectOrder(sessionId, orderId);
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
}
