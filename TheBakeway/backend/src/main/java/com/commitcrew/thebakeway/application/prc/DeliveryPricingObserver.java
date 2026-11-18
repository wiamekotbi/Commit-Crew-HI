package com.commitcrew.thebakeway.application.prc;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.commitcrew.thebakeway.application.console.CommandConsole;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.ord.StateTransitionObserver;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.order.OrderStatus;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

@Component
public class DeliveryPricingObserver implements StateTransitionObserver {

    private final OrderRegistry orderRegistry;
    private final PricingService pricingService;
    private final Optional<PricingBilling> billing;
    private final CommandConsole console;

    public DeliveryPricingObserver(OrderRegistry orderRegistry, PricingService pricingService,
            Optional<PricingBilling> billing, CommandConsole console) {
        this.orderRegistry = orderRegistry;
        this.pricingService = pricingService;
        this.billing = billing;
        this.console = console;
    }

    @Override
    @Transactional
    public void update(StateTransitionEvent event) {
        if (!isDeliveredOrderEvent(event)) {
            return;
        }

        String orderId = event.getEntityId();
        try {
            Order order = findDeliveredOrder(orderId);

            if (hasExistingCharge(orderId)) {
                record(false, "Order " + orderId + " already has a charge");
                return;
            }

            PriceBreakdown pricing = pricingService.calculatePrice(order);
            if (billing.isEmpty()) {
                record(false, "Order " + orderId + ": price computed but no charge recorded; billing integration is not connected");
                return;
            }

            recordCharge(order, pricing, event, orderId);
        } catch (RuntimeException exception) {
            record(false, "Order " + orderId + ": " + exception.getMessage());
            throw exception;
        }
    }

    private boolean isDeliveredOrderEvent(StateTransitionEvent event) {
        return event.getEntityType() == EntityType.ORDER
                && OrderStatus.delivered.name().equals(event.getNewState());
    }

    private Order findDeliveredOrder(String orderId) {
        Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);
        if (!OrderStatus.delivered.name().equals(order.getStatus())) {
            throw new IllegalStateException("Pricing requires a delivered order");
        }
        return order;
    }

    private boolean hasExistingCharge(String orderId) {
        return billing.isPresent() && billing.get().findCharge(orderId).isPresent();
    }

    private void recordCharge(Order order, PriceBreakdown pricing, StateTransitionEvent event, String orderId) {
        boolean recorded = billing.get().recordChargeIfAbsent(order, pricing, event.getTimestamp());
        String detail = "Order " + orderId + (recorded
                ? ": charge recorded, total " + pricing.total() : " already has a charge");
        recordAfterCommitIfSynchronized(recorded, detail);
    }

    private void recordAfterCommitIfSynchronized(boolean success, String detail) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    record(success, detail);
                }
            });
        } else {
            record(success, detail);
        }
    }

    private void record(boolean success, String detail) {
        console.record("system", "SYSTEM", "COMPUTE_ORDER_CHARGE", success, detail);
    }
}
