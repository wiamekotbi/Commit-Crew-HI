package com.commitcrew.thebakeway.ui.registration;

import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

public record SessionResponse(String accountId, Role role) {

    public static SessionResponse from(Session session) {
        return new SessionResponse(session.getAccountId(), session.getRole());
    }
}
