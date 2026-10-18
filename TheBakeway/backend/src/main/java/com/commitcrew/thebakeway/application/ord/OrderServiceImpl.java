package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Service;

import java.util.List;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.menu.MenuItem;

@Service("orderServiceImpl")
public class OrderServiceImpl implements OrderService {

    private final BakeryService bakeryService;
    private final MenuService menuService;

    // Reuse the existing retrieval services rather than duplicating their queries and rules.
    public OrderServiceImpl(BakeryService bakeryService, MenuService menuService) {
        this.bakeryService = bakeryService;
        this.menuService = menuService;
    }

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

    @Override
    public List<Bakery> getOpenBakeries(String sessionId) {
        // Customer-facing calls reach this method through the proxy's access checks.
        return bakeryService.getOpenBakeries();
    }

    @Override
    public List<MenuItem> getMenuForBrowsing(String sessionId, String bakeryId) {
        // MenuService checks bakery availability and preserves sold-out items in the result.
        return menuService.getMenuForBrowsing(bakeryId);
    }
}
