package com.commitcrew.thebakeway.domain.menu;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRegistry extends JpaRepository<MenuItem, String> {
    List<MenuItem> findByAvailableTrue(); // Available means the item has not been discontinued
    // Spring Data JPA automatically provides methods like:
    // - save(MenuItem menuItem)
    // - findById(UUID id)
    // - findAll()
    // - deleteById(UUID id)
}