package com.commitcrew.thebakeway.application.ord;

import java.util.List;
import org.springframework.stereotype.Service;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.bakery.BakeryStatus;

@Service
public class BakeryService {
    private final BakeryRegistry bakeryRegistry;

    public BakeryService(BakeryRegistry bakeryRegistry) {
        this.bakeryRegistry = bakeryRegistry;
    }
    // 1. Fetch all bakeries with a specific status (e.g., open, paused, closed)
    public List<Bakery> getBakeriesByStatus(BakeryStatus status) {
        return bakeryRegistry.findByBakeryStatus(status);
    }

    // 2. Fetch a specific bakery by its ID
    public Bakery getBakeryById(String bakeryId) {
        return bakeryRegistry.findById(bakeryId)
                .orElseThrow(() -> new IllegalArgumentException("Bakery not found with ID: " + bakeryId));
    }

    // 3. Fetch open bakeries
    public List<Bakery> getOpenBakeries() {
        return bakeryRegistry.findByBakeryStatus(BakeryStatus.open);
    }
    
}
