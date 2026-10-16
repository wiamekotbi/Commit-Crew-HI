package com.commitcrew.thebakeway.domain.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRegistry extends JpaRepository<Order, String> {
    // Retrieves orders for a specific bakery and status, sorted by oldest first (FIFO)
    List<Order> findByBakeryIdAndStatusOrderByCreatedAtAsc(String bakeryId, OrderStatus status);
    // Spring Data JPA automatically provides built-in methods like:
    // - save(Order order)
    // - findById(String id)
    // - findAll()
    // - deleteById(String id)
    
}