package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.AccountRegistry;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

/**
 * Resolves bakery ownership from trusted authentication and account data.
 * Menu and bakery commands use this service instead of accepting a bakery ID
 * from the caller as proof that the caller owns that bakery.
 */
@Service
public class BakeryStaffAccessService {

    private final SessionManager sessionManager;
    private final AccountRegistry accountRegistry;

    public BakeryStaffAccessService(SessionManager sessionManager, AccountRegistry accountRegistry) {
        this.sessionManager = sessionManager;
        this.accountRegistry = accountRegistry;
    }

    /**
     * Returns the bakery assigned to the authenticated restaurant account.
     * The Protection Proxy performs the first access check; these checks keep
     * ownership enforcement inside the business implementation as well.
     */
    public String getBakeryIdForSession(String sessionId) {
        Session session = sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));

        BakewayAccount account = accountRegistry.findById(session.getAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated account not found: " + session.getAccountId()));

        if (account.getRole() != Role.RESTAURANT
                || account.getBakeryId() == null
                || account.getBakeryId().isBlank()) {
            throw new SecurityException("Authenticated account is not assigned to a bakery");
        }

        return account.getBakeryId();
    }
}
