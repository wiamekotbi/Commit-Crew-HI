package com.commitcrew.thebakeway.ui.ord;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.ord.InitialStateSummary;
import com.commitcrew.thebakeway.application.ord.InvalidConfigurationException;
import com.commitcrew.thebakeway.application.ord.OrderService;

// Operator command to restore the initial state from the config files (UC-REG-03, UC-ORD-01)
@RestController
@RequestMapping("/api/initial-state")
public class InitialStateController {

    // Must match the cookie issued by LoginController.
    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final OrderService orderService;

    // Spring injects the @Primary OrderServiceProxy, which checks the session and the operator role.
    public InitialStateController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Every session ends after a restore, so the operator has to log in again.
    @PostMapping("/restore")
    public InitialStateSummary restore(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        return orderService.restoreInitialState(sessionId);
    }

    @ExceptionHandler(InvalidConfigurationException.class)
    public ResponseEntity<Map<String, String>> handleInvalidConfiguration(InvalidConfigurationException exception) {
        // The previous state is kept when a config file is invalid.
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(SecurityException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Only the operator can restore the initial state"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationRequired(IllegalStateException exception) {
        if (!"Authentication required".equals(exception.getMessage())) {
            throw exception;
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
    }
}
