package com.commitcrew.thebakeway.domain.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRegistry extends JpaRepository<Order, String> {
    // Retrieves orders for a specific bakery and state, sorted by oldest first (FIFO)
    List<Order> findByBakeryIdAndStateOrderByCreatedAtAsc(String bakeryId, OrderStatus state);
    // Spring Data JPA automatically provides built-in methods like:
    // - save(Order order)
    // - findById(String id)
    // - findAll()
    // - deleteById(String id)
    
}