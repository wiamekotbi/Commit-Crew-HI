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
        if (event.getEntityType() != EntityType.ORDER
                || !OrderStatus.delivered.name().equals(event.getNewState())) {
            return;
        }

        String orderId = event.getEntityId();
        try {
            Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);
            if (!OrderStatus.delivered.name().equals(order.getStatus())) {
                throw new IllegalStateException("Pricing requires a delivered order");
            }

            if (billing.isPresent() && billing.get().findCharge(orderId).isPresent()) {
                record(false, "Order " + orderId + " already has a charge");
                return;
            }

            PriceBreakdown pricing = pricingService.calculatePrice(order);
            if (billing.isEmpty()) {
                record(false, "Order " + orderId + ": price computed but no charge recorded; billing integration is not connected");
                return;
            }

            boolean recorded = billing.get().recordChargeIfAbsent(order, pricing, event.getTimestamp());
            String detail = "Order " + orderId + (recorded
                    ? ": charge recorded, total " + pricing.total() : " already has a charge");
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        record(recorded, detail);
                    }
                });
            } else {
                record(recorded, detail);
            }
        } catch (RuntimeException exception) {
            record(false, "Order " + orderId + ": " + exception.getMessage());
            throw exception;
        }
    }

    private void record(boolean success, String detail) {
        console.record("system", "SYSTEM", "COMPUTE_ORDER_CHARGE", success, detail);
    }
}
