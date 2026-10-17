package com.commitcrew.thebakeway.application.ord;

import java.util.List;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

@Service
public class MenuService {

    private final MenuItemRegistry menuItemRegistry;
    private final BakeryService bakeryService;

    public MenuService(MenuItemRegistry menuItemRegistry, BakeryService bakeryService) {
        this.menuItemRegistry = menuItemRegistry;
        this.bakeryService = bakeryService;
    }

    // 1. Fetch all menu items for a specific bakery (regardless of status)
    public List<MenuItem> getAllMenuItemsByBakeryId(String bakeryId) {
        return menuItemRegistry.findByBakeryId(bakeryId);
    }

    // 2. Fetch menu items for a specific bakery filtered by status (e.g., available or sold_out)
    public List<MenuItem> getMenuItemsByBakeryIdAndStatus(String bakeryId, MenuItemStatus status) {
        return menuItemRegistry.findByBakeryIdAndStatus(bakeryId, status);
    }

    // 3. Fetch a specific menu item by its ID
    public MenuItem getMenuItem(String itemId){
        return menuItemRegistry.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Menu item not found with ID: " + itemId));
    }

    // 4. Fetch menu items for browsing 
    public List<MenuItem> getMenuForBrowsing(String bakeryId) {
        // Reuse the existing lookup so a missing bakery is rejected before querying its menu.
        Bakery bakery = bakeryService.getBakeryById(bakeryId);
        if (bakery.getBakeryStatus() != BakeryStatus.open) {
            throw new BakeryUnavailableException("Bakery " + bakeryId
                    + " is unavailable for browsing: " + bakery.getBakeryStatus());
        }

        // Include sold-out items with their status; an empty menu is a valid result.
        return getAllMenuItemsByBakeryId(bakeryId);
    }

}
