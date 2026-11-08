package com.commitcrew.thebakeway.application.prc;

import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

public record OrderPricingQuote(PriceBreakdown pricing, boolean finalFare) {
}
