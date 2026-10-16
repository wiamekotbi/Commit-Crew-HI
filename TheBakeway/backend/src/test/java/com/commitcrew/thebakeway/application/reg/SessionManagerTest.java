package com.commitcrew.thebakeway.application.reg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

class SessionManagerTest {

    private final SessionManager sessionManager = new SessionManager();

    @Test
    void createsAndFindsSession() {
        Session session = sessionManager.createSession("account-1", Role.CUSTOMER);

        assertNotNull(session.getSessionId());
        assertEquals("account-1", session.getAccountId());
        assertEquals(Role.CUSTOMER, session.getRole());
        assertEquals(session, sessionManager.findSession(session.getSessionId()).orElseThrow());
    }

    @Test
    void invalidatesSession() {
        Session session = sessionManager.createSession("account-1", Role.CUSTOMER);

        sessionManager.invalidateSession(session.getSessionId());

        assertFalse(sessionManager.findSession(session.getSessionId()).isPresent());
    }

    @Test
    void returnsEmptyForMissingSession() {
        assertTrue(sessionManager.findSession("missing").isEmpty());
        assertTrue(sessionManager.findSession(null).isEmpty());
    }
}
