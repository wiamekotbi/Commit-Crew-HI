package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;
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
    private final PromoDiscountService promoDiscountService;
    private final Optional<PricingBilling> billing;

    public OrderPricingService(
            SessionManager sessionManager,
            OrderAccessPolicy orderAccess,
            OrderRegistry orderRegistry,
            PricingService pricingService,
            PromoDiscountService promoDiscountService,
            Optional<PricingBilling> billing) {
        this.sessionManager = sessionManager;
        this.orderAccess = orderAccess;
        this.orderRegistry = orderRegistry;
        this.pricingService = pricingService;
        this.promoDiscountService = promoDiscountService;
        this.billing = billing;
    }

    // Keep the original operation available for callers that do not use a promo code.
    @Transactional(readOnly = true)
    public OrderPricingQuote quoteCart(String sessionId, double itemSubtotal) {
        return quoteCart(sessionId, itemSubtotal, null);
    }

    @Transactional(readOnly = true)
    public OrderPricingQuote quoteCart(String sessionId, double itemSubtotal, String promoCode) {
        Session session = requireSession(sessionId);
        if (session.getRole() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can request a cart price quote");
        }

        // Phase III pricing still calculates the subtotal and fees before PROMO is considered.
        PriceBreakdown pricing = pricingService.calculatePriceForSubtotal(itemSubtotal);
        if (promoCode == null || promoCode.isBlank()) {
            return new OrderPricingQuote(pricing, false);
        }

        PromoDiscountResult promotion = promoDiscountService.applyDiscount(
                promoCode,
                BigDecimal.valueOf(itemSubtotal),
                BigDecimal.valueOf(pricing.total()));

        return new OrderPricingQuote(
                pricing,
                false,
                promotion.promoCode(),
                promotion.discountAmount(),
                promotion.discountedTotal());
    }

    @Transactional(readOnly = true)
    public OrderPricingQuote quoteOrder(String sessionId, String orderId) {
        Session session = requireSession(sessionId);
        Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);
        orderAccess.requireCanView(session, order);

        Optional<OrderSummaryBilling.Charge> savedCharge = billing.flatMap(records -> records.findCharge(orderId))
                .filter(charge -> charge.pricing() != null);
        if (savedCharge.isPresent()) {
            OrderSummaryBilling.Charge charge = savedCharge.get();
            return new OrderPricingQuote(charge.pricing(), true,
                    charge.promoCode(), charge.discountAmount(),
                    BigDecimal.valueOf(charge.pricing().total()));
        }
        PriceBreakdown pricing = pricingService.calculatePrice(order);
        String promoCode = order.getPromoCode();
        BigDecimal discountAmount = BigDecimal.valueOf(pricing.discountAmount());
        return new OrderPricingQuote(pricing, false, promoCode, discountAmount,
                BigDecimal.valueOf(pricing.total()));
    }

    private Session requireSession(String sessionId) {
        return sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }
}
