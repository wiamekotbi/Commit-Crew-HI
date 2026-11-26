package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

@Service
public class OrderPromotionService {

    private final PricingService pricingService;
    private final PromoDiscountService promoDiscountService;

    public OrderPromotionService(
            PricingService pricingService,
            PromoDiscountService promoDiscountService) {
        this.pricingService = pricingService;
        this.promoDiscountService = promoDiscountService;
    }

    public void apply(Order order, String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            return;
        }
        PriceBreakdown pricing = pricingService.calculatePrice(order);
        PromoDiscountResult promotion = promoDiscountService.applyDiscount(
                promoCode,
                BigDecimal.valueOf(pricing.subtotal()),
                BigDecimal.valueOf(pricing.total()));
        order.applyPromotion(promotion.promoCode(), promotion.discountAmount());
    }
}
