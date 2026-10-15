package com.commitcrew.thebakeway.ui.registration;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.reg.ProfileService;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<?> getProfile(@PathVariable String accountId) {
        try {
            BakewayAccount account = profileService.getProfile(accountId);
            return ResponseEntity.ok(ProfileResponse.from(account));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<?> updateProfile(
            @PathVariable String accountId,
            @RequestBody ProfileUpdateRequest request) {

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
}