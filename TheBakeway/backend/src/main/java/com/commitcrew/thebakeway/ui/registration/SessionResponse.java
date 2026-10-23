package com.commitcrew.thebakeway.ui.registration;

import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

// Who is logged in: shown on the dashboard. bakeryId is only set for bakery staff.
public record SessionResponse(String accountId, Role role, String username, String displayName, String bakeryId) {

    public static SessionResponse from(Session session, BakewayAccount account) {
        if (account == null) {
            return new SessionResponse(session.getAccountId(), session.getRole(), null, null, null);
        }
        return new SessionResponse(session.getAccountId(), session.getRole(),
                account.getUsername(), account.getDisplayName(), account.getBakeryId());
    }
}
