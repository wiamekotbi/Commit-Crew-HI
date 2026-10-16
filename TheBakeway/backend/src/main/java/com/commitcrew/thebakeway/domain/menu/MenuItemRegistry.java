package com.commitcrew.thebakeway.domain.menu;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRegistry extends JpaRepository<MenuItem, String> {
    
    // Find all menu items belonging to a specific bakery
    List<MenuItem> findByBakeryId(String bakeryId);
    
    // Find menu items by bakery and status (e.g., available or sold_out)
    List<MenuItem> findByBakeryIdAndStatus(String bakeryId, MenuItemStatus status);

    // Spring Data JPA automatically provides methods like:
    // - save(MenuItem menuItem)
    // - findById(String id)
    // - findAll()
    // - deleteById(String id) -> Used when an item is no longer offered and needs to be removed
}