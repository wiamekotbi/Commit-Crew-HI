package com.commitcrew.thebakeway.application.prc;

import java.time.Instant;

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
        PriceBreakdown pricing) {

    public static OrderLedgerEntryView from(OrderLedgerEntry entry) {
        return new OrderLedgerEntryView(entry.getId(), entry.getOrder().getId(),
                entry.getEntryType().name(), entry.getAmount(), entry.getCurrency(),
                entry.getOccurredAt(), entry.getActorAccountId(), entry.getReason(), entry.getPricing());
    }
}
