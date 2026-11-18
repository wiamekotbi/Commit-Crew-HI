package com.commitcrew.thebakeway.application.prc;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.ord.OrderAccessPolicy;
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
    private final OrderAccessPolicy orderAccess;
    private final OrderRegistry orderRegistry;
    private final PricingService pricingService;
    private final Optional<PricingBilling> billing;

    public OrderPricingService(
            SessionManager sessionManager,
            OrderAccessPolicy orderAccess,
            OrderRegistry orderRegistry,
            PricingService pricingService,
            Optional<PricingBilling> billing) {
        this.sessionManager = sessionManager;
        this.orderAccess = orderAccess;
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
        orderAccess.requireCanView(session, order);

        Optional<PriceBreakdown> savedFare = billing.flatMap(records -> records.findCharge(orderId))
                .map(OrderSummaryBilling.Charge::pricing);
        if (savedFare.isPresent()) {
            return new OrderPricingQuote(savedFare.get(), true);
        }
        return new OrderPricingQuote(pricingService.calculatePrice(order), false);
    }

    private Session requireSession(String sessionId) {
        return sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }
}
