package com.commitcrew.thebakeway.ui.registration;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.reg.LoginService;
import com.commitcrew.thebakeway.application.reg.ProfileService;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;
import com.commitcrew.thebakeway.domain.session.Session;

// A logged-in user views and updates their own profile only (UC-REG-04, R-REG-02)
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;
    private final LoginService loginService;

    public ProfileController(ProfileService profileService, LoginService loginService) {
        this.profileService = profileService;
        this.loginService = loginService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<?> getProfile(
            @CookieValue(value = LoginController.SESSION_COOKIE, required = false) String sessionId,
            @PathVariable String accountId) {
        Optional<ResponseEntity<?>> refused = refuseUnlessOwnProfile(sessionId, accountId);
        if (refused.isPresent()) {
            return refused.get();
        }

        try {
            BakewayAccount account = profileService.getProfile(accountId);
            return ResponseEntity.ok(ProfileResponse.from(account));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<?> updateProfile(
            @CookieValue(value = LoginController.SESSION_COOKIE, required = false) String sessionId,
            @PathVariable String accountId,
            @RequestBody ProfileUpdateRequest request) {
        Optional<ResponseEntity<?>> refused = refuseUnlessOwnProfile(sessionId, accountId);
        if (refused.isPresent()) {
            return refused.get();
        }

        try {
            BakewayAccount account = profileService.updateProfile(
                    accountId,
                    request.displayName(),
                    request.contactEmail(),
                    request.deliveryAddress());

            return ResponseEntity.ok(ProfileResponse.from(account));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // No session: 401. Someone else's profile: 403 (UC-REG-04 alternate flow 3a).
    private Optional<ResponseEntity<?>> refuseUnlessOwnProfile(String sessionId, String accountId) {
        Optional<Session> session = loginService.findSession(sessionId);
        if (session.isEmpty()) {
            return Optional.of(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required")));
        }

        if (!session.get().getAccountId().equals(accountId)) {
            return Optional.of(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You can only view and edit your own profile")));
        }

        return Optional.empty();
    }
}
