package com.commitcrew.thebakeway.domain.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRegistry extends JpaRepository<Order, String> {
    // Retrieves orders for a specific bakery and status, sorted by oldest first (FIFO)
    List<Order> findByBakeryIdAndStatusOrderByCreatedAtAsc(String bakeryId, OrderStatus status);
    // A customer's orders, newest first (UC-ORD-12)
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    // A bakery's orders, oldest first (staff see incoming orders and the queue in arrival order)
    List<Order> findByBakeryIdOrderByCreatedAtAsc(String bakeryId);
    // Spring Data JPA automatically provides built-in methods like:
    // - save(Order order)
    // - findById(String id)
    // - findAll()
    // - deleteById(String id)
    
}