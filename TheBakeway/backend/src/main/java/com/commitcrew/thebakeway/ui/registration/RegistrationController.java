package com.commitcrew.thebakeway.ui.registration;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.reg.RegistrationService;
import com.commitcrew.thebakeway.domain.account.BakewayAccount;

@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @RequestBody RegistrationRequest request) {

        try {
            BakewayAccount account = registrationService.registerCustomer(
                    request.name(),
                    request.username(),
                    request.password(),
                    request.confirmPassword(),
                    request.deliveryAddress());

            return ResponseEntity.ok(Map.of(
                    "message", "Account created",
                    "accountId", account.getAccountId()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()));
        }
    }
}