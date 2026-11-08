package com.commitcrew.thebakeway.application.dlv;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.order.OrderStatus;
import com.commitcrew.thebakeway.domain.session.Session;

/**
 * Operator use cases for inspecting waiting dispatch work and replacing a
 * courier who abandoned an assignment by going offline.
 *
 * The service reuses PermissionPolicy for authorization and DispatchService
 * for candidate selection, offer exclusivity, and FIFO fallback behavior.
 */
@Service
public class OperatorDispatchService {

    static final String VIEW_WAITING_ORDERS = "VIEW_WAITING_ORDERS";
    static final String REASSIGN_STALLED_ORDER = "REASSIGN_STALLED_ORDER";

    private final SessionManager sessionManager;
    private final PermissionPolicy permissionPolicy;
    private final OrderRegistry orderRegistry;
    private final CourierRegistry courierRegistry;
    private final DispatchWaitingQueue waitingQueue;
    private final DispatchService dispatchService;

    public OperatorDispatchService(
            SessionManager sessionManager,
            PermissionPolicy permissionPolicy,
            OrderRegistry orderRegistry,
            CourierRegistry courierRegistry,
            DispatchWaitingQueue waitingQueue,
            DispatchService dispatchService) {
        this.sessionManager = sessionManager;
        this.permissionPolicy = permissionPolicy;
        this.orderRegistry = orderRegistry;
        this.courierRegistry = courierRegistry;
        this.waitingQueue = waitingQueue;
        this.dispatchService = dispatchService;
    }

    /**
     * Lists READY orders that either have no available courier or were left by
     * an assigned courier who went offline. Duplicate IDs are merged so an
     * order can never appear twice in the operator dashboard.
     */
    @Transactional(readOnly = true)
    public List<WaitingOrderView> getWaitingOrders(String sessionId) {
        requirePermission(sessionId, VIEW_WAITING_ORDERS);

        Map<String, WaitingOrderView> views = new LinkedHashMap<>();
        List<String> queuedOrderIds = waitingQueue.snapshot();
        for (int index = 0; index < queuedOrderIds.size(); index++) {
            String orderId = queuedOrderIds.get(index);
            int queuePosition = index + 1;
            orderRegistry.findById(orderId)
                    .filter(this::isReady)
                    .ifPresent(order -> views.put(orderId,
                            waitingView(order, queuePosition, null)));
        }

        // Sort repository results for stable output after the FIFO entries.
        courierRegistry.findByStalledOrderIdIsNotNull().stream()
                .sorted((left, right) -> left.getStalledOrderId()
                        .compareTo(right.getStalledOrderId()))
                .forEach(courier -> addStalledOrder(views, courier));

        return List.copyOf(views.values());
    }

    /**
     * Releases one explicit stalled reference and lets the existing dispatch
     * workflow seek a new courier. If selection finds nobody, DispatchService
     * leaves the READY order in its FIFO waiting queue.
     */
    @Transactional
    public DispatchReassignmentResult reassignStalledOrder(
            String sessionId, String orderId) {
        requirePermission(sessionId, REASSIGN_STALLED_ORDER);
        requireOrderId(orderId);

        Order order = orderRegistry.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (!isReady(order)) {
            throw new IllegalStateException(
                    "Only READY stalled orders can be reassigned");
        }

        Courier previousCourier = courierRegistry.findByStalledOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Order is not eligible for reassignment: " + orderId));

        // The entity verifies that this courier is offline and owns this exact
        // stalled reference before any dispatch state is changed.
        previousCourier.releaseStalledOrder(orderId);
        courierRegistry.save(previousCourier);

        return dispatchService.dispatchReadyOrder(orderId)
                .map(offer -> new DispatchReassignmentResult(
                        orderId,
                        previousCourier.getCourierId(),
                        offer.courierId(),
                        false))
                .orElseGet(() -> new DispatchReassignmentResult(
                        orderId,
                        previousCourier.getCourierId(),
                        null,
                        true));
    }

    private void addStalledOrder(
            Map<String, WaitingOrderView> views, Courier courier) {
        String orderId = courier.getStalledOrderId();
        orderRegistry.findById(orderId)
                .filter(this::isReady)
                .ifPresent(order -> {
                    WaitingOrderView queued = views.get(orderId);
                    Integer queuePosition = queued == null
                            ? null
                            : queued.queuePosition();
                    views.put(orderId, waitingView(
                            order,
                            queuePosition,
                            courier.getCourierId()));
                });
    }

    private WaitingOrderView waitingView(
            Order order, Integer queuePosition, String stalledCourierId) {
        return new WaitingOrderView(
                order.getId(),
                order.getBakeryId(),
                order.getStatus(),
                queuePosition,
                stalledCourierId,
                stalledCourierId != null);
    }

    private boolean isReady(Order order) {
        return OrderStatus.ready.name().equals(order.getStatus());
    }

    private Session requirePermission(String sessionId, String operation) {
        Session session = sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authentication required"));
        if (!permissionPolicy.isAllowed(operation, session.getRole())) {
            throw new SecurityException(
                    "Only operators may manage stalled dispatch assignments");
        }
        return session;
    }

    private void requireOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID is required");
        }
    }
}
