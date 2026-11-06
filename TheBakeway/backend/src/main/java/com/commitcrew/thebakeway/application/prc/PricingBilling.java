package com.commitcrew.thebakeway.application.prc;

import java.time.Instant;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

public interface PricingBilling extends OrderSummaryBilling {

    boolean recordChargeIfAbsent(Order order, PriceBreakdown pricing, Instant deliveredAt);
}
