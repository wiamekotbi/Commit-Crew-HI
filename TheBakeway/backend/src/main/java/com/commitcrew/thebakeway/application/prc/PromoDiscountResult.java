package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;

// A record is used because it's values are immutable and it provides a
// concise syntax for creating data carrier classes.
public record PromoDiscountResult(
    //Fields
    String promoCode,
    BigDecimal discountAmount,
    BigDecimal discountedTotal) {

    public PromoDiscountResult{

        // Validating the each field to ensure they are not null or invalid
        if (promoCode == null || promoCode.isBlank()) {
            throw new IllegalArgumentException("Promo code cannot be null or blank");
        }

        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount amount cannot be null or negative");
        }

        if (discountedTotal == null || discountedTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discounted total cannot be null or negative");
        }

    }

}
