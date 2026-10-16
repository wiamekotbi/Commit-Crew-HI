package com.commitcrew.thebakeway.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRegistry extends JpaRepository<Order, String> {
    // Spring Data JPA automatically provides built-in methods like:
    // - save(Order order)
    // - findById(String id)
    // - findAll()
    // - deleteById(String id)
}