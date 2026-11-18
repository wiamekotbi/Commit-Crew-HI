package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PromoValidationService {

    private final Clock clock;

    @Autowired
    public PromoValidationService() {
        this(Clock.systemDefaultZone());
    }

    PromoValidationService(Clock clock) {
        this.clock = clock;
    }

    public void validate(LocalDate expirationDate, BigDecimal minimumOrderValue, BigDecimal orderValue) {
        if (expirationDate == null || minimumOrderValue == null || minimumOrderValue.signum() < 0) {
            throw new IllegalArgumentException("Invalid promo configuration");
        }
        if (orderValue == null || orderValue.signum() < 0) {
            throw new IllegalArgumentException("Order value must be nonnegative");
        }
        if (LocalDate.now(clock).isAfter(expirationDate)) {
            throw new IllegalArgumentException("This promo code has expired");
        }
        if (orderValue.compareTo(minimumOrderValue) < 0) {
            throw new IllegalArgumentException(
                    "This promo code requires an order value of at least " + minimumOrderValue.toPlainString());
        }
    }
}
