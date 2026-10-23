package com.commitcrew.thebakeway.application.ord;

import java.util.List;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

public interface OrderService {

    OrderTransitionResult placeOrder(String sessionId, PlaceOrderRequest request);

    OrderTransitionResult cancelOrder(String sessionId, String orderId);

    OrderTransitionResult acceptOrder(String sessionId, String orderId);

    OrderTransitionResult rejectOrder(String sessionId, String orderId, String reason);

    OrderTransitionResult startPreparing(String sessionId, String orderId);

    OrderTransitionResult markReady(String sessionId, String orderId);

    List<OrderView> listOrdersForCustomer(String sessionId);

    List<OrderView> listOrdersForBakery(String sessionId);

    List<OrderView> listAllOrders(String sessionId, String bakeryId, String status);

    OrderView getOrder(String sessionId, String orderId);

    List<Bakery> getOpenBakeries(String sessionId);

    List<MenuItem> getMenuForBrowsing(String sessionId, String bakeryId);

    MenuItem addMenuItem(String sessionId, String name, String description, double price);

    MenuItem editMenuItem(
            String sessionId, String itemId, String name, String description, double price);

    MenuItem updateMenuItemAvailability(
            String sessionId, String itemId, MenuItemStatus newStatus);

    Bakery pauseBakery(String sessionId, String bakeryId);

    Bakery reopenBakery(String sessionId, String bakeryId);

    InitialStateSummary restoreInitialState(String sessionId);
}
