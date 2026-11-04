package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.location.Coordinates;

class NearestCourierSelectionStrategyTest {

    private static final Coordinates BAKERY_LOCATION = new Coordinates(45.5000, -73.5700);

    private final CourierSelectionStrategy strategy = new NearestCourierSelectionStrategy();

    @Test
    void selectsNearestIdleCourier() {
        Courier farther = idleCourier("courier-2", 45.5200, -73.5700);
        Courier nearest = idleCourier("courier-1", 45.5010, -73.5700);

        Optional<Courier> selected = strategy.selectCourier(
                BAKERY_LOCATION, List.of(farther, nearest));

        assertEquals("courier-1", selected.orElseThrow().getCourierId());
    }

    @Test
    void resolvesEqualDistanceUsingLowestCourierId() {
        // Both candidates are equally far from the bakery. Reversing their
        // input order proves the result comes from the ID rule, not list order.
        Courier higherId = idleCourier("courier-20", 45.5100, -73.5700);
        Courier lowerId = idleCourier("courier-10", 45.4900, -73.5700);

        Optional<Courier> selected = strategy.selectCourier(
                BAKERY_LOCATION, List.of(higherId, lowerId));

        assertEquals("courier-10", selected.orElseThrow().getCourierId());
    }

    @Test
    void excludesOfflineAssignedAndCarryingCouriers() {
        Courier offline = configuredCourier("courier-offline", 45.5001, -73.5700);

        Courier assigned = idleCourier("courier-assigned", 45.5002, -73.5700);
        assigned.assignOrder("order-1");

        Courier carrying = idleCourier("courier-carrying", 45.5003, -73.5700);
        carrying.assignOrder("order-2");
        carrying.markPickedUp();

        Courier eligible = idleCourier("courier-idle", 45.5300, -73.5700);

        Optional<Courier> selected = strategy.selectCourier(
                BAKERY_LOCATION, List.of(offline, assigned, carrying, eligible));

        assertEquals("courier-idle", selected.orElseThrow().getCourierId());
    }

    @Test
    void returnsEmptyWhenNoEligibleCourierExists() {
        Courier offline = configuredCourier("courier-offline", 45.5001, -73.5700);
        Courier assigned = idleCourier("courier-assigned", 45.5002, -73.5700);
        assigned.assignOrder("order-1");

        assertTrue(strategy.selectCourier(
                BAKERY_LOCATION, List.of(offline, assigned)).isEmpty());
        assertTrue(strategy.selectCourier(BAKERY_LOCATION, List.of()).isEmpty());
    }

    @Test
    void excludesCourierWithoutConfiguredCoordinates() {
        // The legacy constructor is retained for compatibility, but such a
        // courier cannot safely participate in nearest-location selection.
        Courier missingLocation = new Courier("courier-no-location");
        missingLocation.goOnDuty();

        assertTrue(strategy.selectCourier(
                BAKERY_LOCATION, List.of(missingLocation)).isEmpty());
    }

    @Test
    void rejectsMissingSelectionInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.selectCourier(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> strategy.selectCourier(BAKERY_LOCATION, null));
    }

    private Courier idleCourier(String id, double latitude, double longitude) {
        Courier courier = configuredCourier(id, latitude, longitude);
        courier.goOnDuty();
        return courier;
    }

    private Courier configuredCourier(String id, double latitude, double longitude) {
        return new Courier(id, new Coordinates(latitude, longitude));
    }
}
