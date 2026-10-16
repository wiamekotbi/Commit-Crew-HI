package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Service;

@Service("orderServiceImpl")
public class OrderServiceImpl implements OrderService {

    @Override
    public void placeOrder(String sessionId) {
        // Awaiting full order implementation from ORD module merge.
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
    public void rejectOrder(String sessionId, String orderId) {
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
