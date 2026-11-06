package com.commitcrew.thebakeway.application.dlv;

import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;
import com.commitcrew.thebakeway.domain.location.Coordinates;

public record CourierView(
        String courierId,
        CourierStatus status,
        String currentOrderId,
        String stalledOrderId,
        Coordinates coordinates,
        DispatchOffer activeOffer) {

    public static CourierView from(
            Courier courier, DispatchOffer activeOffer) {
        return new CourierView(
                courier.getCourierId(),
                courier.getStatus(),
                courier.getCurrentOrderId(),
                courier.getStalledOrderId(),
                courier.getCoordinates(),
                activeOffer);
    }
}
