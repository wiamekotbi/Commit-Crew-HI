package com.commitcrew.thebakeway.domain.bakery;

import java.util.List; 
import org.springframework.data.jpa.repository.JpaRepository;

public interface BakeryRegistry extends JpaRepository<Bakery, String> {

    // Find all bakeries with a specific status (e.g., open, paused, closed)
    List<Bakery> findByBakeryStatus(BakeryStatus bakeryStatus);

    // Id lookup for a specific bakery is already provided by JpaRepository's findById(String id) method.
    // Therefore no additional method is needed for that functionality.
}
