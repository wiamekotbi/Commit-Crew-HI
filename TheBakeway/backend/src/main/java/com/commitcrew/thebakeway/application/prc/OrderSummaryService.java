package com.commitcrew.thebakeway.application.prc;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.ord.OrderAccessPolicy;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.EventRepository;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.session.Session;

@Service
public class OrderSummaryService {

    private final SessionManager sessionManager;
    private final OrderRegistry orderRegistry;
    private final EventRepository eventRepository;
    private final Optional<OrderSummaryBilling> billing;
    private final OrderAccessPolicy orderAccess;

    public OrderSummaryService(
            SessionManager sessionManager,
            OrderRegistry orderRegistry,
            EventRepository eventRepository,
            Optional<OrderSummaryBilling> billing,
            OrderAccessPolicy orderAccess) {
        this.sessionManager = sessionManager;
        this.orderRegistry = orderRegistry;
        this.eventRepository = eventRepository;
        this.billing = billing;
        this.orderAccess = orderAccess;
    }

    @Transactional(readOnly = true)
    public OrderSummary getSummary(String sessionId, String orderId) {
        Session session = sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));

        if (session.getRole() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can view order summaries");
        }

        Order order = orderRegistry.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        orderAccess.requireCanView(session, order);   // role is CUSTOMER here, so this checks ownership only

        if (!"delivered".equals(order.getStatus()) && !"refunded".equals(order.getStatus())) {
            throw new IllegalStateException("Order summary is available only after delivery");
        }

        OrderSummaryBilling records = billing.orElseThrow(() ->
                new IllegalStateException("Order summary is unavailable until billing integration is connected"));
        OrderSummaryBilling.Charge charge = records.findCharge(orderId)
                .filter(saved -> saved.chargeId() != null && !saved.chargeId().isBlank()
                        && saved.pricing() != null)
                .orElseThrow(() -> new IllegalStateException("Order summary is not available: no recorded charge"));

        Instant deliveredAt = eventRepository
                .findByEntityTypeAndEntityIdOrderByTimestampAsc(EntityType.ORDER, orderId).stream()
                .filter(event -> "delivered".equals(event.getNewState()))
                .map(StateTransitionEvent::getTimestamp)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Order summary is not available: delivery timestamp is missing"));

        return new OrderSummary(
                order.getId(),
                order.getStatus(),
                order.getOrderItems().stream()
                        .map(line -> new OrderSummary.Item(line.getItemName(), line.getQuantity(),
                                line.getUnitPrice(), line.calculateSubtotal()))
                        .toList(),
                charge.chargeId(),
                charge.pricing(),
                deliveredAt,
                records.findRefunds(orderId));
    }
}
