package com.commitcrew.thebakeway.application.ord;

public interface OrderService {

    void placeOrder(String sessionId);

    void cancelOrder(String sessionId, String orderId);

    void acceptOrder(String sessionId, String orderId);

    void rejectOrder(String sessionId, String orderId);

    void startPreparing(String sessionId, String orderId);

    void markReady(String sessionId, String orderId);
}
