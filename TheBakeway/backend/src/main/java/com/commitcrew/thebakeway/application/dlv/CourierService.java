package com.commitcrew.thebakeway.application.dlv;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;

@Service
public class CourierService {

    private final CourierRegistry courierRegistry;
    private final DispatchService dispatchService;

    public CourierService(
            CourierRegistry courierRegistry,
            DispatchService dispatchService) {
        this.courierRegistry = courierRegistry;
        this.dispatchService = dispatchService;
    }

    /**
     * Accepts the courier's active offer and closes Sukhi's transient dispatch
     * state only after the courier has been persisted as ASSIGNED.
     */
    @Transactional
    public DispatchOffer acceptOffer(String courierId, String offerId) {
        DispatchOffer offer = dispatchService.getActiveOfferForCourier(
                offerId, courierId);
        Courier courier = existingCourier(courierId);

        courier.acceptOffer(offer.orderId());
        courierRegistry.save(courier);

        return dispatchService.recordOfferAccepted(offerId, courierId);
    }

    /**
     * Declines the courier's active offer. The courier remains IDLE while
     * dispatch removes the offer and returns its order to the waiting queue.
     */
    @Transactional
    public DispatchOffer declineOffer(String courierId, String offerId) {
        DispatchOffer offer = dispatchService.getActiveOfferForCourier(
                offerId, courierId);
        Courier courier = existingCourier(courierId);

        courier.declineOffer();
        dispatchService.recordOfferDeclined(offerId, courierId);

        return offer;
    }

    private Courier existingCourier(String courierId) {
        if (courierId == null || courierId.isBlank()) {
            throw new IllegalArgumentException("Courier ID is required.");
        }
        return courierRegistry.findById(courierId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Courier not found with ID: " + courierId));
    }
}
