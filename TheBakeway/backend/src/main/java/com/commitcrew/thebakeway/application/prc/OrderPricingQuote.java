package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;

import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

public record OrderPricingQuote(
        PriceBreakdown pricing,
        boolean finalFare,
        String promoCode,
        BigDecimal discountAmount,
        BigDecimal discountedTotal) {

    public OrderPricingQuote(PriceBreakdown pricing, boolean finalFare) {
        this(
                pricing,
                finalFare,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(pricing.total()));
    }
}
