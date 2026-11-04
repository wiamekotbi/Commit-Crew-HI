package com.commitcrew.thebakeway.application.dlv;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;
import com.commitcrew.thebakeway.domain.location.Coordinates;

/**
 * Selects the nearest idle courier using straight-line coordinate distance.
 * Equal-distance candidates are ordered by courier ID so selection remains
 * deterministic regardless of database result order.
 */
@Component
public class NearestCourierSelectionStrategy implements CourierSelectionStrategy {

    @Override
    public Optional<Courier> selectCourier(
            Coordinates bakeryCoordinates,
            List<Courier> couriers) {
        if (bakeryCoordinates == null) {
            throw new IllegalArgumentException("Bakery coordinates are required.");
        }
        if (couriers == null) {
            throw new IllegalArgumentException("Courier candidates are required.");
        }

        return couriers.stream()
                // IDLE already means on duty, unassigned, and not carrying an order
                // in Wiame's courier lifecycle. A location is also required before
                // a candidate can participate in a distance comparison.
                .filter(courier -> courier != null
                        && courier.getStatus() == CourierStatus.IDLE
                        && courier.getCoordinates() != null)
                .min(Comparator
                        .comparingDouble((Courier courier) -> squaredDistance(
                                bakeryCoordinates, courier.getCoordinates()))
                        .thenComparing(Courier::getCourierId));
    }

    /**
     * Squared Euclidean distance produces the same ordering as taking a square
     * root, while avoiding unnecessary work. The project requirement asks for
     * straight-line comparison rather than road-network travel distance.
     */
    private double squaredDistance(Coordinates first, Coordinates second) {
        double latitudeDifference = first.getLatitude() - second.getLatitude();
        double longitudeDifference = first.getLongitude() - second.getLongitude();
        return latitudeDifference * latitudeDifference
                + longitudeDifference * longitudeDifference;
    }
}
