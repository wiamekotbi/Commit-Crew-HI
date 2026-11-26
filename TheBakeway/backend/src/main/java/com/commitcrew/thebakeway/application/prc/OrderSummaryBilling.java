package com.commitcrew.thebakeway.application.prc;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

public interface OrderSummaryBilling {

    Optional<Charge> findCharge(String orderId);

    List<Refund> findRefunds(String orderId);

    record Charge(String chargeId, PriceBreakdown pricing, String promoCode, BigDecimal discountAmount) {
        public Charge(String chargeId, PriceBreakdown pricing) {
            this(chargeId, pricing, null, BigDecimal.ZERO);
        }
    }

    record Refund(String refundId, double amount, Instant refundedAt, String reason) {
    }
}
