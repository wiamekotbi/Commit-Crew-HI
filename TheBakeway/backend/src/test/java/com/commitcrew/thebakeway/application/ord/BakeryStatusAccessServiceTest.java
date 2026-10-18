package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

class BakeryStatusAccessServiceTest {

    @Test
    void restaurantMayPauseItsAssignedBakery() {
        SessionManager sessionManager = org.mockito.Mockito.mock(SessionManager.class);
        BakeryStaffAccessService staffAccess =
                org.mockito.Mockito.mock(BakeryStaffAccessService.class);
        BakeryStatusAccessService accessService =
                new BakeryStatusAccessService(sessionManager, staffAccess);
        when(sessionManager.findSession("session-1")).thenReturn(Optional.of(
                new Session("session-1", "staff-1", Role.RESTAURANT)));
        when(staffAccess.getBakeryIdForSession("session-1")).thenReturn("bakery-1");

        String target = accessService.resolvePauseTarget("session-1", "bakery-1");

        assertEquals("bakery-1", target);
        verify(staffAccess).getBakeryIdForSession("session-1");
    }

    @Test
    void restaurantCannotPauseAnotherBakery() {
        SessionManager sessionManager = org.mockito.Mockito.mock(SessionManager.class);
        BakeryStaffAccessService staffAccess =
                org.mockito.Mockito.mock(BakeryStaffAccessService.class);
        BakeryStatusAccessService accessService =
                new BakeryStatusAccessService(sessionManager, staffAccess);
        when(sessionManager.findSession("session-1")).thenReturn(Optional.of(
                new Session("session-1", "staff-1", Role.RESTAURANT)));
        when(staffAccess.getBakeryIdForSession("session-1")).thenReturn("bakery-1");

        assertThrows(SecurityException.class,
                () -> accessService.resolvePauseTarget("session-1", "bakery-2"));
    }

    @Test
    void restaurantCannotReopenEvenItsAssignedBakery() {
        SessionManager sessionManager = org.mockito.Mockito.mock(SessionManager.class);
        BakeryStaffAccessService staffAccess =
                org.mockito.Mockito.mock(BakeryStaffAccessService.class);
        BakeryStatusAccessService accessService =
                new BakeryStatusAccessService(sessionManager, staffAccess);
        when(sessionManager.findSession("session-1")).thenReturn(Optional.of(
                new Session("session-1", "staff-1", Role.RESTAURANT)));

        assertThrows(SecurityException.class,
                () -> accessService.resolveReopenTarget("session-1", "bakery-1"));

        // Ownership cannot override the operator-only reopen rule.
        verify(staffAccess, never()).getBakeryIdForSession("session-1");
    }

    @Test
    void operatorMayPauseOrReopenSelectedBakery() {
        SessionManager sessionManager = org.mockito.Mockito.mock(SessionManager.class);
        BakeryStaffAccessService staffAccess =
                org.mockito.Mockito.mock(BakeryStaffAccessService.class);
        BakeryStatusAccessService accessService =
                new BakeryStatusAccessService(sessionManager, staffAccess);
        when(sessionManager.findSession("session-1")).thenReturn(Optional.of(
                new Session("session-1", "operator-1", Role.OPERATOR)));

        assertEquals("bakery-2",
                accessService.resolvePauseTarget("session-1", "bakery-2"));
        assertEquals("bakery-2",
                accessService.resolveReopenTarget("session-1", "bakery-2"));

        // Operators target bakeries by ID and do not need a staff ownership record.
        verify(staffAccess, never()).getBakeryIdForSession("session-1");
    }

    @Test
    void missingSessionIsRejectedBeforeRoleOrOwnershipChecks() {
        SessionManager sessionManager = org.mockito.Mockito.mock(SessionManager.class);
        BakeryStaffAccessService staffAccess =
                org.mockito.Mockito.mock(BakeryStaffAccessService.class);
        BakeryStatusAccessService accessService =
                new BakeryStatusAccessService(sessionManager, staffAccess);
        when(sessionManager.findSession("missing-session")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> accessService.resolvePauseTarget("missing-session", "bakery-1"));
        verify(staffAccess, never()).getBakeryIdForSession("missing-session");
    }
}
