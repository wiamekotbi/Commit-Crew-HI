package com.commitcrew.thebakeway.application.prc;

import java.time.Instant;
import java.util.List;

import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

public record OrderSummary(
        String orderId,
        String status,
        List<Item> items,
        String chargeId,
        PriceBreakdown pricing,
        Instant deliveredAt,
        List<OrderSummaryBilling.Refund> refunds) {

    public record Item(String itemName, int quantity, double unitPrice, double subtotal) {
    }
}
