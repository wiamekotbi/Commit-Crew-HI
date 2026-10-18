package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.session.Session;

/**
 * Resolves the bakery that an authenticated account may change. A bakery ID
 * supplied by a caller identifies the requested target; it never proves that
 * restaurant staff own that bakery.
 */
@Service
public class BakeryStatusAccessService {

    private final SessionManager sessionManager;
    private final BakeryStaffAccessService bakeryStaffAccessService;

    public BakeryStatusAccessService(
            SessionManager sessionManager,
            BakeryStaffAccessService bakeryStaffAccessService) {
        this.sessionManager = sessionManager;
        this.bakeryStaffAccessService = bakeryStaffAccessService;
    }

    /**
     * Restaurant staff may pause only their assigned bakery. Operators may
     * pause any explicitly selected bakery as required by UC-ORD-11.
     */
    public String resolvePauseTarget(String sessionId, String requestedBakeryId) {
        Session session = requireSession(sessionId);
        requireBakeryId(requestedBakeryId);

        if (session.getRole() == Role.OPERATOR) {
            return requestedBakeryId;
        }

        if (session.getRole() == Role.RESTAURANT) {
            String ownedBakeryId = bakeryStaffAccessService.getBakeryIdForSession(sessionId);
            if (!ownedBakeryId.equals(requestedBakeryId)) {
                throw new SecurityException(
                        "Restaurant staff may pause only their assigned bakery");
            }
            return ownedBakeryId;
        }

        throw new SecurityException("Account cannot change bakery status");
    }

    /** Reopening is reserved for operators, even when restaurant staff own the bakery. */
    public String resolveReopenTarget(String sessionId, String requestedBakeryId) {
        Session session = requireSession(sessionId);
        requireBakeryId(requestedBakeryId);

        if (session.getRole() != Role.OPERATOR) {
            throw new SecurityException("Only operators may reopen bakeries");
        }

        return requestedBakeryId;
    }

    private Session requireSession(String sessionId) {
        return sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
    }

    private void requireBakeryId(String bakeryId) {
        if (bakeryId == null || bakeryId.isBlank()) {
            throw new IllegalArgumentException("Bakery ID is required");
        }
    }
}
