package com.commitcrew.thebakeway.application.ord;

import java.util.List;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;

@Service
public class MenuService {

    private final MenuItemRegistry menuItemRegistry;

    public MenuService(MenuItemRegistry menuItemRegistry) {
        this.menuItemRegistry = menuItemRegistry;
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
}