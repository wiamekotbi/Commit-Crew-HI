package com.commitcrew.thebakeway.application.dlv;

import java.time.Instant;
import java.util.List;

import com.commitcrew.thebakeway.application.prc.OrderLedgerEntryView;

public record CompletedDeliveryView(
        String orderId,
        Instant deliveredAt,
        List<OrderLedgerEntryView> ledgerEntries) {
}