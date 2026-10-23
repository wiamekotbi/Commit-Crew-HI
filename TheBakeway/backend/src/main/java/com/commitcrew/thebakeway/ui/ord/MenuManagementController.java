package com.commitcrew.thebakeway.ui.ord;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.domain.menu.MenuItem;

/**
 * HTTP boundary for restaurant menu management. This controller translates
 * requests only; authentication, permissions, bakery ownership, persistence,
 * and transition publication remain in the existing application services.
 */
@RestController
@RequestMapping("/api/menu-items")
public class MenuManagementController {

    // This must match the cookie created by LoginController.
    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final OrderService orderService;

    /**
     * Depending on the interface keeps the controller behind the Protection
     * Proxy because Spring selects the @Primary OrderService implementation.
     */
    public MenuManagementController(OrderService orderService) {
        this.orderService = orderService;
    }

    // The staff member's own bakery menu, with every item's status
    @GetMapping
    public List<MenuItem> listOwnMenuItems(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        return orderService.listOwnMenuItems(sessionId);
    }

    @PostMapping
    public ResponseEntity<MenuItem> addMenuItem(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @Valid @RequestBody MenuItemDetailsRequest request) {
        // No bakery ID is accepted here. OrderService resolves it from the
        // authenticated restaurant account before creating the item.
        MenuItem created = orderService.addMenuItem(
                sessionId, request.name(), request.description(), request.price());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{itemId}")
    public MenuItem editMenuItem(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("itemId") String itemId,
            @Valid @RequestBody MenuItemDetailsRequest request) {
        // Ownership is checked against the stored item's bakery before the
        // service changes any descriptive field.
        return orderService.editMenuItem(
                sessionId, itemId, request.name(), request.description(), request.price());
    }

    @PatchMapping("/{itemId}/availability")
    public MenuItem updateAvailability(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("itemId") String itemId,
            @Valid @RequestBody MenuItemAvailabilityRequest request) {
        // The service publishes an Observer event only when the status really changes.
        return orderService.updateMenuItemAvailability(sessionId, itemId, request.status());
    }

    /** Maps Protection Proxy permission failures to an HTTP authorization response. */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(SecurityException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied for menu management"));
    }

    /**
     * The current proxy represents a missing or expired session with this
     * exact exception. Unrelated state failures are rethrown rather than being
     * incorrectly reported as authentication problems.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationRequired(
            IllegalStateException exception) {
        if (!"Authentication required".equals(exception.getMessage())) {
            throw exception;
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
    }

    /** Returns domain input failures, such as a negative price, as bad requests. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
