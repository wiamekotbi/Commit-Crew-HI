package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

@Service
public class PromoDiscountService {

    private final PromoConfigurationReader promoConfigurationReader;
    private final PromoValidationService promoValidationService;

    public PromoDiscountService(
            PromoConfigurationReader promoConfigurationReader,
            PromoValidationService promoValidationService) {
        this.promoConfigurationReader = promoConfigurationReader;
        this.promoValidationService = promoValidationService;
    }

    public PromoDiscountResult applyDiscount(
            String promoCode,
            BigDecimal orderValue,
            BigDecimal currentTotal) {

        // Do not accept null or blank promo codes
        if (promoCode == null || promoCode.isBlank()) {
            throw new IllegalArgumentException("Promo code cannot be null or blank");
        }

        // Do not accept a null or negative total
        if (currentTotal == null || currentTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current total cannot be null or negative");
        }

        // Match codes without making customers use the exact capitalization from the config file.
        String enteredCode = promoCode.trim();
        PromoConfiguration promotion = promoConfigurationReader.read().stream()
                .filter(config -> config.code().equalsIgnoreCase(enteredCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown promo code"));

        // Reuse the existing expiration and minimum-order validation rules.
        promoValidationService.validate(

                promotion.expirationDate(),
                promotion.minimumOrderAmount(),
                orderValue
            );

        // A promotion must never make the total negative.
        BigDecimal effectiveDiscount = promotion.discountAmount().min(currentTotal);
        BigDecimal discountedTotal = currentTotal.subtract(effectiveDiscount);

        return new PromoDiscountResult(

                promotion.code(),
                effectiveDiscount,
                discountedTotal
            );

    }
}
