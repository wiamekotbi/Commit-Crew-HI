package com.commitcrew.thebakeway.application.reg;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

@Service
public class SessionManager {

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    public Session createSession(String accountId, Role role) {
        String sessionId = UUID.randomUUID().toString();
        Session session = new Session(sessionId, accountId, role);
        sessions.put(sessionId, session);
        return session;
    }

    public Optional<Session> findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void invalidateSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public void invalidateAllSessions(){
        sessions.clear();
    }
}
