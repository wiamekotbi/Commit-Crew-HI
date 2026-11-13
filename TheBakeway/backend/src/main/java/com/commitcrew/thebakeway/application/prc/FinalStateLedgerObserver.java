package com.commitcrew.thebakeway.application.prc;

import java.util.Set;

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
import com.commitcrew.thebakeway.domain.pricing.LedgerEntryType;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;

// Records a rejected or cancelled order in the ledger with a total charge of zero
// (R-PRC-03, UC-PRC-03). Delivered orders are recorded by DeliveryPricingObserver.
// Runs in the transaction of the command, so a failure rolls the transition back.
@Component
public class FinalStateLedgerObserver implements StateTransitionObserver {

    private static final Set<String> NO_CHARGE_STATES = Set.of(
            OrderStatus.rejected.name(),
            OrderStatus.cancelled.name());

    private final OrderRegistry orderRegistry;
    private final OrderLedgerRepository ledgerRepository;
    private final CommandConsole console;

    public FinalStateLedgerObserver(
            OrderRegistry orderRegistry, OrderLedgerRepository ledgerRepository, CommandConsole console) {
        this.orderRegistry = orderRegistry;
        this.ledgerRepository = ledgerRepository;
        this.console = console;
    }

    @Override
    @Transactional
    public void update(StateTransitionEvent event) {
        if (event.getEntityType() != EntityType.ORDER || !NO_CHARGE_STATES.contains(event.getNewState())) {
            return;
        }

        String orderId = event.getEntityId();
        Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);

        // An order is recorded only once (UC-PRC-03 2a)
        if (ledgerRepository.findByOrder_IdAndEntryType(orderId, LedgerEntryType.CHARGE).isPresent()) {
            return;
        }

        ledgerRepository.save(OrderLedgerEntry.noCharge(order, event.getNewState(), event.getTimestamp()));
        recordAfterCommit("Order " + orderId + " recorded in the ledger: " + event.getNewState() + ", total 0.00");
    }

    private void recordAfterCommit(String detail) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            console.record("system", "SYSTEM", "RECORD_ORDER_IN_LEDGER", true, detail);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                console.record("system", "SYSTEM", "RECORD_ORDER_IN_LEDGER", true, detail);
            }
        });
    }
}
