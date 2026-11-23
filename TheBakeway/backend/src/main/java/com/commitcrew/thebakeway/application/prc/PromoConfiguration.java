package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Defines one promotion loaded from the shared configuration file.
 * Monetary values use BigDecimal so discount calculations remain exact.
 */
public record PromoConfiguration(
        String code,
        LocalDate expirationDate,
        BigDecimal minimumOrderAmount,
        BigDecimal discountAmount) {
}
