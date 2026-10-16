package com.commitcrew.thebakeway.domain.menu;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRegistry extends JpaRepository<MenuItem, UUID> { // UUID rather than String so the ID can be automatically generated
    List<MenuItem> findByAvailableTrue();
    // Spring Data JPA automatically provides methods like:
    // - save(MenuItem menuItem)
    // - findById(UUID id)
    // - findAll()
    // - deleteById(UUID id)
}