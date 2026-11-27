package com.commitcrew.thebakeway.application.prc;

import java.time.Instant;
import java.time.LocalDateTime;

import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

public record OrderLedgerEntryView(
        String ledgerEntryId,
        String orderId,
        String entryType,
        double amount,
        String currency,
        Instant occurredAt,
        String actorAccountId,
        String reason,
        PriceBreakdown pricing,
        String finalState,
        LocalDateTime placedAt,
        String promoCode,
        java.math.BigDecimal discountAmount) {

    public OrderLedgerEntryView(
            String ledgerEntryId,
            String orderId,
            String entryType,
            double amount,
            String currency,
            Instant occurredAt,
            String actorAccountId,
            String reason,
            PriceBreakdown pricing,
            String finalState,
            LocalDateTime placedAt) {
        this(ledgerEntryId, orderId, entryType, amount, currency, occurredAt, actorAccountId, reason,
                pricing, finalState, placedAt, null, java.math.BigDecimal.ZERO);
    }

    public static OrderLedgerEntryView from(OrderLedgerEntry entry) {
        return new OrderLedgerEntryView(entry.getId(), entry.getOrder().getId(),
                entry.getEntryType().name(), entry.getAmount(), entry.getCurrency(),
                entry.getOccurredAt(), entry.getActorAccountId(), entry.getReason(), entry.getPricing(),
                entry.getFinalState(), entry.getOrder().getCreatedAt(),
                entry.getPromoCode(), entry.getPromoDiscountAmount());
    }
}
