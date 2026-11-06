package com.commitcrew.thebakeway.application.dlv;

import java.util.List;
import java.util.Optional;

import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.location.Coordinates;

/**
 * Strategy contract for choosing one courier for an order.
 *
 * Dispatch owns when selection occurs and what happens after a candidate is
 * chosen. Implementations own only the selection rule, which lets the rule be
 * replaced without changing offer, queue, or assignment logic.
 */
public interface CourierSelectionStrategy {

    /**
     * Selects one eligible courier for the bakery location.
     *
     * @param bakeryCoordinates fixed pickup location of the order's bakery
     * @param couriers candidates known to dispatch
     * @return the selected courier, or empty when nobody is eligible
     */
    Optional<Courier> selectCourier(
            Coordinates bakeryCoordinates,
            List<Courier> couriers);
}
