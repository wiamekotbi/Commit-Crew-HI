package com.commitcrew.thebakeway.application.ord;

import java.util.List;

public record PlaceOrderRequest(
        String bakeryId,
        List<OrderItemRequest> items,
        String deliveryAddress,
        String promoCode) {

    public PlaceOrderRequest(String bakeryId, List<OrderItemRequest> items, String deliveryAddress) {
        this(bakeryId, items, deliveryAddress, null);
    }
}
