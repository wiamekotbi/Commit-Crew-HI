package com.commitcrew.thebakeway.application.dlv;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierRegistry;
import com.commitcrew.thebakeway.domain.courier.CourierStatus;
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
    private final CourierRegistry courierRegistry;
    private final CourierSelectionService courierSelectionService;
    private final ActiveDispatchOffers activeOffers;
    private final DispatchWaitingQueue waitingQueue;

    public DispatchService(
            OrderRegistry orderRegistry,
            CourierRegistry courierRegistry,
            CourierSelectionService courierSelectionService,
            ActiveDispatchOffers activeOffers,
            DispatchWaitingQueue waitingQueue) {
        this.orderRegistry = orderRegistry;
        this.courierRegistry = courierRegistry;
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

        // An accepted assignment remains READY until pickup. Checking the
        // courier assignment prevents that valid lifecycle gap from producing
        // a second offer for the same order.
        if (courierRegistry.findByCurrentOrderId(orderId).isPresent()) {
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

    /**
     * Hook for Wiame's acceptance command. The caller must first complete and
     * save Courier.assignOrder(orderId); this method then closes dispatch state.
     */
    public synchronized DispatchOffer recordOfferAccepted(
            String offerId, String courierId) {
        DispatchOffer offer = matchingOffer(offerId, courierId);
        Courier courier = courierRegistry.findById(courierId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Courier not found with ID: " + courierId));

        if (courier.getStatus() != CourierStatus.ASSIGNED
                || !offer.orderId().equals(courier.getCurrentOrderId())) {
            throw new IllegalStateException(
                    "Courier assignment must be completed before accepting the dispatch offer.");
        }

        activeOffers.removeByOfferId(offerId);
        waitingQueue.remove(offer.orderId());
        return offer;
    }

    /**
     * Hook for Wiame's decline command. It releases the courier and returns the
     * order to FIFO waiting state, but deliberately does not retry immediately;
     * a later courier-availability signal starts the next attempt.
     */
    public synchronized DispatchOffer recordOfferDeclined(
            String offerId, String courierId) {
        DispatchOffer offer = matchingOffer(offerId, courierId);
        activeOffers.removeByOfferId(offerId);
        waitingQueue.enqueue(offer.orderId());
        return offer;
    }

    /**
     * Hook for Wiame's courier workflow after a successful transition into
     * IDLE, such as going on duty or completing a delivery. Waiting orders are
     * retried in FIFO order, and each new offer reserves its courier before the
     * next order is considered.
     *
     * Decline must not call this hook: a decline leaves the courier IDLE but is
     * not a new availability transition, so retrying immediately could offer
     * the same order repeatedly without an external state change.
     */
    public synchronized List<DispatchOffer> retryWaitingOrdersWhenCourierBecomesIdle(
            String courierId) {
        if (courierId == null || courierId.isBlank()) {
            throw new IllegalArgumentException("Courier ID is required.");
        }

        Courier courier = courierRegistry.findById(courierId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Courier not found with ID: " + courierId));
        if (courier.getStatus() != CourierStatus.IDLE) {
            return List.of();
        }

        // Iterate over a snapshot because successful dispatch removes entries
        // from the live queue while the retry is in progress.
        List<DispatchOffer> createdOffers = new ArrayList<>();
        for (String orderId : waitingQueue.snapshot()) {
            dispatchReadyOrder(orderId).ifPresent(createdOffers::add);
        }
        return List.copyOf(createdOffers);
    }

    private DispatchOffer matchingOffer(String offerId, String courierId) {
        if (courierId == null || courierId.isBlank()) {
            throw new IllegalArgumentException("Courier ID is required.");
        }

        DispatchOffer offer = activeOffers.findByOfferId(offerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Active dispatch offer not found: " + offerId));
        if (!offer.courierId().equals(courierId)) {
            throw new SecurityException("Dispatch offer belongs to another courier.");
        }
        return offer;
    }
}
