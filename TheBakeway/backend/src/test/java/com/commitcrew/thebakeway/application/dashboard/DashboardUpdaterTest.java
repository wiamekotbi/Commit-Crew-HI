package com.commitcrew.thebakeway.application.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.commitcrew.thebakeway.domain.event.EntityType;
import com.commitcrew.thebakeway.domain.event.StateTransitionEvent;

class DashboardUpdaterTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void appliesTransitionImmediatelyWhenNoTransactionIsActive() {
        DashboardReadModel readModel = new DashboardReadModel();
        DashboardUpdater updater = new DashboardUpdater(readModel);

        updater.update(event());

        assertEquals("paused",
                readModel.findState(EntityType.BAKERY, "bakery-1").orElseThrow());
    }

    @Test
    void waitsForCommitBeforeUpdatingDashboardProjection() {
        DashboardReadModel readModel = new DashboardReadModel();
        DashboardUpdater updater = new DashboardUpdater(readModel);
        TransactionSynchronizationManager.initSynchronization();

        updater.update(event());

        // The uncommitted state must not leak into dashboard reads.
        assertTrue(readModel.findState(EntityType.BAKERY, "bakery-1").isEmpty());

        // Simulate the transaction manager's successful-commit callback.
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        assertEquals("paused",
                readModel.findState(EntityType.BAKERY, "bakery-1").orElseThrow());
    }

    private StateTransitionEvent event() {
        return new StateTransitionEvent(
                "event-1",
                EntityType.BAKERY,
                "bakery-1",
                "open",
                "paused",
                Instant.parse("2026-10-18T18:30:00Z"));
    }
}
