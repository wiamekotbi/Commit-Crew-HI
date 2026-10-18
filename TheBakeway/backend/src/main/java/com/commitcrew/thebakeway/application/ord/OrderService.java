package com.commitcrew.thebakeway.application.ord;

import java.util.List;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

public interface OrderService {

    String placeOrder(String sessionId, PlaceOrderRequest request);

    void cancelOrder(String sessionId, String orderId);

    void acceptOrder(String sessionId, String orderId);

    void rejectOrder(String sessionId, String orderId, String reason);

    void startPreparing(String sessionId, String orderId);

    void markReady(String sessionId, String orderId);

    List<Bakery> getOpenBakeries(String sessionId); 

    List<MenuItem> getMenuForBrowsing(String sessionId, String bakeryId);

    MenuItem addMenuItem(String sessionId, String name, String description, double price);

    MenuItem editMenuItem(
            String sessionId, String itemId, String name, String description, double price);

    MenuItem updateMenuItemAvailability(
            String sessionId, String itemId, MenuItemStatus newStatus);

    Bakery pauseBakery(String sessionId, String bakeryId);

    Bakery reopenBakery(String sessionId, String bakeryId);
}
