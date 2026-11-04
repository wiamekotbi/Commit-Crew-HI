package com.commitcrew.thebakeway.application.dlv;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.order.OrderStatus;

/**
 * Coordinates one dispatch attempt for a READY order.
 *
 * This service creates offers and manages waiting state, but does not accept or
 * decline on behalf of a courier. Wiame's courier commands will call the
 * response hooks added in the next integration step.
 */
@Service
public class DispatchService {

    private final OrderRegistry orderRegistry;
    private final CourierSelectionService courierSelectionService;
    private final ActiveDispatchOffers activeOffers;
    private final DispatchWaitingQueue waitingQueue;

    public DispatchService(
            OrderRegistry orderRegistry,
            CourierSelectionService courierSelectionService,
            ActiveDispatchOffers activeOffers,
            DispatchWaitingQueue waitingQueue) {
        this.orderRegistry = orderRegistry;
        this.courierSelectionService = courierSelectionService;
        this.activeOffers = activeOffers;
        this.waitingQueue = waitingQueue;
    }

    /**
     * Offers a READY order to the nearest unreserved IDLE courier. If none is
     * available, the order retains READY status and joins the waiting queue.
     * Repeating the call while an offer is active returns that same offer.
     */
    public synchronized Optional<DispatchOffer> dispatchReadyOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID is required.");
        }

        Order order = orderRegistry.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);

        if (!OrderStatus.ready.name().equals(order.getStatus())) {
            // Retry code can encounter an order cancelled after it was queued.
            // Clean stale transient state and never offer an ineligible order.
            waitingQueue.remove(orderId);
            activeOffers.removeByOrderId(orderId);
            return Optional.empty();
        }

        Optional<DispatchOffer> existingOffer = activeOffers.findByOrderId(orderId);
        if (existingOffer.isPresent()) {
            return existingOffer;
        }

        Optional<Courier> candidate = courierSelectionService.selectCourierForBakery(
                order.getBakeryId(), activeOffers.reservedCourierIds());

        if (candidate.isEmpty()) {
            waitingQueue.enqueue(orderId);
            return Optional.empty();
        }

        DispatchOffer offer = new DispatchOffer(
                UUID.randomUUID().toString(),
                orderId,
                candidate.get().getCourierId(),
                Instant.now());
        activeOffers.add(offer);
        waitingQueue.remove(orderId);
        return Optional.of(offer);
    }
}
