package com.commitcrew.thebakeway.application.ord;

public interface OrderService {

    String placeOrder(String sessionId, PlaceOrderRequest request);

    void cancelOrder(String sessionId, String orderId);

    void acceptOrder(String sessionId, String orderId);

    void rejectOrder(String sessionId, String orderId, String reason);

    void startPreparing(String sessionId, String orderId);

    void markReady(String sessionId, String orderId);
}
