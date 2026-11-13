package com.commitcrew.thebakeway.application.dlv;

import java.time.Instant;

// One delivery the courier carried (R-PRC-04). Charges are left out: a courier does
// not see what the customer paid (UC-PRC-04 4b, Appendix A).
public record CompletedDeliveryView(
        String orderId,
        String bakeryId,
        String deliveryAddress,
        String orderStatus,
        Instant deliveredAt) {
}
