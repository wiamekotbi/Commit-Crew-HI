package com.commitcrew.thebakeway.application.prc;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.ord.BakeryStaffAccessService;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.LedgerEntryType;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;
import com.commitcrew.thebakeway.domain.session.Session;

@Service
public class OrderBillingService {

    private final SessionManager sessionManager;
    private final PermissionPolicy permissionPolicy;
    private final BakeryStaffAccessService bakeryStaffAccessService;
    private final OrderRegistry orderRegistry;
    private final OrderLedgerRepository ledgerRepository;

    public OrderBillingService(
            SessionManager sessionManager,
            PermissionPolicy permissionPolicy,
            BakeryStaffAccessService bakeryStaffAccessService,
            OrderRegistry orderRegistry,
            OrderLedgerRepository ledgerRepository) {
        this.sessionManager = sessionManager;
        this.permissionPolicy = permissionPolicy;
        this.bakeryStaffAccessService = bakeryStaffAccessService;
        this.orderRegistry = orderRegistry;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public OrderLedgerEntryView refundDeliveredOrder(String sessionId, String orderId, String reason) {
        Session session = requireSession(sessionId);
        if (!permissionPolicy.isAllowed("REFUND_ORDER", session.getRole())) {
            throw new SecurityException("Only operators can refund orders");
        }

        Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);
        if (!"delivered".equals(order.getStatus())) {
            throw new IllegalStateException("Only delivered orders can be refunded");
        }

        OrderLedgerEntry charge = ledgerRepository
                .findByOrderIdAndEntryTypeForUpdate(orderId, LedgerEntryType.CHARGE)
                .orElseThrow(() -> new IllegalStateException("Order has no recorded charge"));
        if (ledgerRepository.findByOrder_IdAndEntryType(orderId, LedgerEntryType.REFUND).isPresent()) {
            throw new IllegalStateException("Order has already been refunded");
        }

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("A refund reason is required");
        }

        OrderLedgerEntry refund = OrderLedgerEntry.refund(
                order, charge.getAmount(), Instant.now(), session.getAccountId(), normalizedReason);
        return OrderLedgerEntryView.from(ledgerRepository.save(refund));
    }

    @Transactional(readOnly = true)
    public List<OrderLedgerEntryView> getLedger(String sessionId, String orderId) {
        Session session = requireSession(sessionId);
        Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);
        verifyCanView(session, order);
        return ledgerRepository.findByOrder_IdOrderByOccurredAtAsc(orderId).stream()
                .map(OrderLedgerEntryView::from)
                .toList();
    }

    private void verifyCanView(Session session, Order order) {
        if (session.getRole() == Role.OPERATOR) {
            return;
        }
        if (session.getRole() == Role.CUSTOMER && session.getAccountId().equals(order.getCustomerId())) {
            return;
        }
        if (session.getRole() == Role.RESTAURANT
                && order.getBakeryId().equals(bakeryStaffAccessService.getBakeryIdForSession(session.getSessionId()))) {
            return;
        }
        throw new OrderNotFoundException();
    }

    private Session requireSession(String sessionId) {
        return sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }
}
