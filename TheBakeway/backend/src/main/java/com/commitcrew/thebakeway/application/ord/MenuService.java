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

    /**
     * Adds an item to the bakery derived from the authenticated staff account.
     * JPA assigns the unique item ID when the entity is persisted, and the
     * domain constructor ensures every new item begins as available.
     */
    public MenuItem addMenuItem(String bakeryId, String name, String description, double price) {
        MenuItem menuItem = new MenuItem(
                bakeryId, name, description, price, MenuItemStatus.available);
        return menuItemRegistry.save(menuItem);
    }

    /**
     * Edits only descriptive fields on an item owned by the staff member's
     * bakery. Ownership is checked before mutating the managed entity.
     */
    public MenuItem editMenuItem(
            String bakeryId,
            String itemId,
            String name,
            String description,
            double price) {
        MenuItem menuItem = getMenuItem(itemId);
        if (!bakeryId.equals(menuItem.getBakeryId())) {
            throw new SecurityException("Menu item does not belong to the authenticated bakery");
        }

        menuItem.updateDetails(name, description, price);
        return menuItemRegistry.save(menuItem);
    }

}
