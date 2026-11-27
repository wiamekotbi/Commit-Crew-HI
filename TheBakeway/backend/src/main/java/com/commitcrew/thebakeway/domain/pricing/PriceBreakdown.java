
package com.commitcrew.thebakeway.domain.pricing;

public record PriceBreakdown(
        double subtotal,
        double deliveryFee,
        double serviceFee,
        double smallOrderSurcharge,
        double total,
        double discountAmount) {

    public PriceBreakdown(
            double subtotal,
            double deliveryFee,
            double serviceFee,
            double smallOrderSurcharge,
            double total) {
        this(subtotal, deliveryFee, serviceFee, smallOrderSurcharge, total, 0);
    }
}
