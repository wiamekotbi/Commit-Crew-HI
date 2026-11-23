package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.application.ord.OrderAccessPolicy;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

class OrderPricingServiceTest {

    private final SessionManager sessionManager = new SessionManager();
    private final PricingService pricingService = mock(PricingService.class);
    private final PromoDiscountService promoDiscountService = mock(PromoDiscountService.class);
    private final OrderPricingService service = new OrderPricingService(
            sessionManager,
            mock(OrderAccessPolicy.class),
            mock(OrderRegistry.class),
            pricingService,
            promoDiscountService,
            Optional.empty());

    @Test
    void returnsExistingPriceWhenNoPromoCodeIsProvided() {
        String sessionId = sessionManager.createSession("customer-1", Role.CUSTOMER).getSessionId();
        PriceBreakdown pricing = new PriceBreakdown(8, 4, 2, 3, 17);
        when(pricingService.calculatePriceForSubtotal(8)).thenReturn(pricing);

        OrderPricingQuote result = service.quoteCart(sessionId, 8);

        assertEquals(pricing, result.pricing());
        assertNull(result.promoCode());
        assertEquals(BigDecimal.ZERO, result.discountAmount());
        assertEquals(BigDecimal.valueOf(17.0), result.discountedTotal());
        verifyNoInteractions(promoDiscountService);
    }

    @Test
    void treatsBlankPromoCodeAsNoPromotion() {
        String sessionId = sessionManager.createSession("customer-1", Role.CUSTOMER).getSessionId();
        PriceBreakdown pricing = new PriceBreakdown(8, 4, 2, 3, 17);
        when(pricingService.calculatePriceForSubtotal(8)).thenReturn(pricing);

        OrderPricingQuote result = service.quoteCart(sessionId, 8, "  ");

        assertNull(result.promoCode());
        assertEquals(BigDecimal.valueOf(17.0), result.discountedTotal());
        verifyNoInteractions(promoDiscountService);
    }

    @Test
    void appliesPromotionAfterExistingPriceCalculation() {
        String sessionId = sessionManager.createSession("customer-1", Role.CUSTOMER).getSessionId();
        PriceBreakdown pricing = new PriceBreakdown(25, 4, 2, 0, 31);
        PromoDiscountResult discount = new PromoDiscountResult(
                "SAVE5", new BigDecimal("5.00"), new BigDecimal("26.00"));
        when(pricingService.calculatePriceForSubtotal(25)).thenReturn(pricing);
        when(promoDiscountService.applyDiscount(
                "save5", BigDecimal.valueOf(25.0), BigDecimal.valueOf(31.0)))
                .thenReturn(discount);

        OrderPricingQuote result = service.quoteCart(sessionId, 25, "save5");

        assertEquals(pricing, result.pricing());
        assertEquals("SAVE5", result.promoCode());
        assertEquals(new BigDecimal("5.00"), result.discountAmount());
        assertEquals(new BigDecimal("26.00"), result.discountedTotal());
        verify(promoDiscountService).applyDiscount(
                "save5", BigDecimal.valueOf(25.0), BigDecimal.valueOf(31.0));
    }

    @Test
    void rejectsNonCustomerBeforeCalculatingPrice() {
        String sessionId = sessionManager.createSession("operator-1", Role.OPERATOR).getSessionId();

        assertThrows(SecurityException.class, () -> service.quoteCart(sessionId, 25, "SAVE5"));

        verifyNoInteractions(pricingService, promoDiscountService);
    }
}
