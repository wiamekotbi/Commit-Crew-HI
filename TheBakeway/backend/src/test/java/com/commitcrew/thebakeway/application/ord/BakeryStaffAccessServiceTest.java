package com.commitcrew.thebakeway.application.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

class BakeryStaffAccessServiceTest {

    @Test
    void resolvesBakeryFromAuthenticatedRestaurantAccount() {
        SessionManager sessionManager = mock(SessionManager.class);
        AccountRegistry accountRegistry = mock(AccountRegistry.class);
        BakeryStaffAccessService service = new BakeryStaffAccessService(sessionManager, accountRegistry);
        Session session = new Session("session-1", "staff-1", Role.RESTAURANT);
        BakewayAccount account = restaurantAccount("bakery-1");
        when(sessionManager.findSession("session-1")).thenReturn(Optional.of(session));
        when(accountRegistry.findById("staff-1")).thenReturn(Optional.of(account));

        String bakeryId = service.getBakeryIdForSession("session-1");

        assertEquals("bakery-1", bakeryId);
    }

    @Test
    void rejectsMissingSession() {
        SessionManager sessionManager = mock(SessionManager.class);
        AccountRegistry accountRegistry = mock(AccountRegistry.class);
        BakeryStaffAccessService service = new BakeryStaffAccessService(sessionManager, accountRegistry);
        when(sessionManager.findSession("missing-session")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.getBakeryIdForSession("missing-session"));
    }

    @Test
    void rejectsSessionWhoseAccountNoLongerExists() {
        SessionManager sessionManager = mock(SessionManager.class);
        AccountRegistry accountRegistry = mock(AccountRegistry.class);
        BakeryStaffAccessService service = new BakeryStaffAccessService(sessionManager, accountRegistry);
        Session session = new Session("session-1", "missing-account", Role.RESTAURANT);
        when(sessionManager.findSession("session-1")).thenReturn(Optional.of(session));
        when(accountRegistry.findById("missing-account")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.getBakeryIdForSession("session-1"));
    }

    @Test
    void rejectsNonRestaurantAccount() {
        SessionManager sessionManager = mock(SessionManager.class);
        AccountRegistry accountRegistry = mock(AccountRegistry.class);
        BakeryStaffAccessService service = new BakeryStaffAccessService(sessionManager, accountRegistry);
        Session session = new Session("session-1", "customer-1", Role.CUSTOMER);
        BakewayAccount account = new BakewayAccount(
                "customer-1", "customer", "password", "Customer",
                "customer@example.com", "Montreal", Role.CUSTOMER);
        when(sessionManager.findSession("session-1")).thenReturn(Optional.of(session));
        when(accountRegistry.findById("customer-1")).thenReturn(Optional.of(account));

        assertThrows(SecurityException.class,
                () -> service.getBakeryIdForSession("session-1"));
    }

    private BakewayAccount restaurantAccount(String bakeryId) {
        return new BakewayAccount(
                "staff-1", "staff", "password", "Bakery Staff",
                "staff@example.com", null, Role.RESTAURANT, bakeryId);
    }
}
