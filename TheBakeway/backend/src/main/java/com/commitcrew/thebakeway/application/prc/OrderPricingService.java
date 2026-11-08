package com.commitcrew.thebakeway.application.prc;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.ord.BakeryStaffAccessService;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;
import com.commitcrew.thebakeway.domain.session.Session;

@Service
public class OrderPricingService {

    private final SessionManager sessionManager;
    private final BakeryStaffAccessService bakeryStaffAccessService;
    private final OrderRegistry orderRegistry;
    private final PricingService pricingService;
    private final Optional<PricingBilling> billing;

    public OrderPricingService(
            SessionManager sessionManager,
            BakeryStaffAccessService bakeryStaffAccessService,
            OrderRegistry orderRegistry,
            PricingService pricingService,
            Optional<PricingBilling> billing) {
        this.sessionManager = sessionManager;
        this.bakeryStaffAccessService = bakeryStaffAccessService;
        this.orderRegistry = orderRegistry;
        this.pricingService = pricingService;
        this.billing = billing;
    }

    @Transactional(readOnly = true)
    public OrderPricingQuote quoteCart(String sessionId, double itemSubtotal) {
        Session session = requireSession(sessionId);
        if (session.getRole() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can request a cart price quote");
        }
        return new OrderPricingQuote(pricingService.calculatePriceForSubtotal(itemSubtotal), false);
    }

    @Transactional(readOnly = true)
    public OrderPricingQuote quoteOrder(String sessionId, String orderId) {
        Session session = requireSession(sessionId);
        Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);
        verifyCanView(session, order);

        Optional<PriceBreakdown> savedFare = billing.flatMap(records -> records.findCharge(orderId))
                .map(OrderSummaryBilling.Charge::pricing);
        if (savedFare.isPresent()) {
            return new OrderPricingQuote(savedFare.get(), true);
        }
        return new OrderPricingQuote(pricingService.calculatePrice(order), false);
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
