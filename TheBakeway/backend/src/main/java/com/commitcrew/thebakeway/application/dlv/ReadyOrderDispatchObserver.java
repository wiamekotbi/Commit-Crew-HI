package com.commitcrew.thebakeway.application.dlv;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.commitcrew.thebakeway.application.ord.StateTransitionObserver;
import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;
import com.commitcrew.thebakeway.domain.order.OrderStatus;

/**
 * Observer integration between Aya's order lifecycle and courier dispatch.
 *
 * OrderService already publishes one transition after a successful markReady
 * command. Listening to that event avoids changing the State classes or adding
 * a second READY transition path solely for dispatch.
 */
@Component
public class ReadyOrderDispatchObserver implements StateTransitionObserver {

    private final DispatchService dispatchService;

    public ReadyOrderDispatchObserver(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Override
    public void update(StateTransitionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Transition event is required.");
        }

        if (event.getEntityType() != EntityType.ORDER
                || !OrderStatus.ready.name().equals(event.getNewState())) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // The event is published inside OrderService's transaction. Waiting
            // until commit ensures dispatch can load the persisted READY order
            // and cannot offer an order whose state change later rolls back.
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatchService.dispatchReadyOrder(event.getEntityId());
                        }
                    });
            return;
        }

        // Direct publisher use in focused tests and non-transactional tooling
        // still performs the same dispatch action immediately.
        dispatchService.dispatchReadyOrder(event.getEntityId());
    }
}
