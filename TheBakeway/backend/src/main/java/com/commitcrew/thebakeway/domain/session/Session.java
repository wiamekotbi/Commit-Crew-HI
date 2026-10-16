package com.commitcrew.thebakeway.domain.session;

import com.commitcrew.thebakeway.domain.account.Role;

public final class Session {

    private final String sessionId;
    private final String accountId;
    private final Role role;

    public Session(String sessionId, String accountId, Role role) {
        this.sessionId = sessionId;
        this.accountId = accountId;
        this.role = role;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public Role getRole() {
        return role;
    }
}
