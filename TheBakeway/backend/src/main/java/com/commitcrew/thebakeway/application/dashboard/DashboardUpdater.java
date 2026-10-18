package com.commitcrew.thebakeway.application.dashboard;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.commitcrew.thebakeway.application.ord.StateTransitionObserver;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

/**
 * Concrete Observer that projects successful transitions onto the dashboard.
 * Database-backed service calls defer the projection until commit so a rolled
 * back state change never becomes visible in the dashboard read model.
 */
@Component
public class DashboardUpdater implements StateTransitionObserver {

    private final DashboardReadModel dashboardReadModel;

    public DashboardUpdater(DashboardReadModel dashboardReadModel) {
        this.dashboardReadModel = dashboardReadModel;
    }

    @Override
    public void update(StateTransitionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Transition event is required");
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Apply only after the state change and EventRecorder save commit.
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dashboardReadModel.apply(event);
                        }
                    });
            return;
        }

        // Direct publisher use in tests or non-transactional tools still works.
        dashboardReadModel.apply(event);
    }
}
