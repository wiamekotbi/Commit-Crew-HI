package com.commitcrew.thebakeway.application.dlv;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.ord.BakeryNotFoundException;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.bakery.BakeryRegistry;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;

/**
 * Application boundary that supplies persisted bakery and courier data to the
 * configured selection Strategy.
 *
 * This service deliberately stops after identifying a candidate. Issue #61
 * owns offer creation, waiting, and assignment, so selection never reserves a
 * courier or changes courier lifecycle state on its own.
 */
@Service
public class CourierSelectionService {

    private final BakeryRegistry bakeryRegistry;
    private final CourierRegistry courierRegistry;
    private final CourierSelectionStrategy selectionStrategy;

    public CourierSelectionService(
            BakeryRegistry bakeryRegistry,
            CourierRegistry courierRegistry,
            CourierSelectionStrategy selectionStrategy) {
        this.bakeryRegistry = bakeryRegistry;
        this.courierRegistry = courierRegistry;
        this.selectionStrategy = selectionStrategy;
    }

    /**
     * Selects a candidate for an order originating at the requested bakery.
     * The IDLE query follows Wiame's lifecycle definition: IDLE means the
     * courier is on duty and does not currently hold an assignment.
     */
    @Transactional(readOnly = true)
    public Optional<Courier> selectCourierForBakery(String bakeryId) {
        return selectCourierForBakery(bakeryId, Set.of());
    }

    /**
     * Selects a candidate while excluding couriers already reserved by active
     * offers. Those couriers remain IDLE until they accept, so status alone is
     * not enough to prevent conflicting simultaneous offers.
     */
    @Transactional(readOnly = true)
    public Optional<Courier> selectCourierForBakery(
            String bakeryId, Set<String> excludedCourierIds) {
        if (bakeryId == null || bakeryId.isBlank()) {
            throw new IllegalArgumentException("Bakery ID is required.");
        }
        if (excludedCourierIds == null) {
            throw new IllegalArgumentException("Excluded courier IDs are required.");
        }

        Bakery bakery = bakeryRegistry.findById(bakeryId)
                .orElseThrow(() -> new BakeryNotFoundException(
                        "Bakery not found with ID: " + bakeryId));

        if (bakery.getCoordinates() == null) {
            throw new IllegalStateException(
                    "Bakery has no coordinates configured: " + bakeryId);
        }

        List<Courier> idleCouriers = courierRegistry.findByStatus(CourierStatus.IDLE).stream()
                .filter(courier -> !excludedCourierIds.contains(courier.getCourierId()))
                .toList();
        return selectionStrategy.selectCourier(bakery.getCoordinates(), idleCouriers);
    }
}
