
package com.commitcrew.thebakeway.domain.pricing;

public record PriceBreakdown(
        double subtotal,
        double deliveryFee,
        double serviceFee,
        double smallOrderSurcharge,
        double total) {
}
