package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Service;

@Service("orderServiceImpl")
public class OrderServiceImpl implements OrderService {

    @Override
    public String placeOrder(String sessionId, PlaceOrderRequest request) {
        // Awaiting full order implementation from ORD module merge.
        throw new UnsupportedOperationException("Placing orders is not available yet");
    }

    @Override
    public void cancelOrder(String sessionId, String orderId) {
        // Awaiting full order implementation from ORD module merge.
    }

    @Override
    public void acceptOrder(String sessionId, String orderId) {
        // Awaiting full order implementation from ORD module merge.
    }

    @Override
    public void rejectOrder(String sessionId, String orderId, String reason) {
        // Awaiting full order implementation from ORD module merge.
    }

    @Override
    public void startPreparing(String sessionId, String orderId) {
        // Awaiting full order implementation from ORD module merge.
    }

    @Override
    public void markReady(String sessionId, String orderId) {
        // Awaiting full order implementation from ORD module merge.
    }
}
