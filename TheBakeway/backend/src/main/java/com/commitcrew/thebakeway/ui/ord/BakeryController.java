package com.commitcrew.thebakeway.ui.ord;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.ord.BakeryNotFoundException;
import com.commitcrew.thebakeway.application.ord.BakeryUnavailableException;
import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.domain.bakery.Bakery;
import com.commitcrew.thebakeway.domain.menu.MenuItem;

// The UI layer translates HTTP requests and responses; application services own browsing rules.
@RestController
@RequestMapping("/api/bakeries")
public class BakeryController {

    // Must match the cookie issued by LoginController; this controller does not create sessions.
    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final OrderService orderService;

    // Depend on the interface: Spring selects the @Primary OrderServiceProxy,
    // which checks sessions and permissions before delegating to the implementation.
    public BakeryController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<Bakery> getOpenBakeries(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        // Missing cookies become null so the proxy can reject them as unauthenticated.
        // An empty list is returned as HTTP 200 with [], not as an error.
        return orderService.getOpenBakeries(sessionId);
    }

    // Operator only: every bakery, including paused and closed ones
    @GetMapping("/all")
    public List<Bakery> listAllBakeries(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        return orderService.listAllBakeries(sessionId);
    }

    @GetMapping("/{bakeryId}/menu")
    public List<MenuItem> getMenuForBrowsing(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("bakeryId") String bakeryId) {
        // Availability checks and sold-out visibility remain in the application services.
        return orderService.getMenuForBrowsing(sessionId, bakeryId);
    }

    // Local handlers translate failures for these endpoints only, preserving other controllers.
    @ExceptionHandler(BakeryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleBakeryNotFound(BakeryNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(BakeryUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleBakeryUnavailable(BakeryUnavailableException exception) {
        // Spring selects this more specific handler over the IllegalStateException handler.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(SecurityException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied for browsing"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationRequired(IllegalStateException exception) {
        // The existing proxy uses this type/message instead of a dedicated authentication exception.
        // Restrict the mapping so unrelated application failures are not misreported as login errors.
        if (!"Authentication required".equals(exception.getMessage())) {
            throw exception;
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
    }

    @GetMapping("/{bakeryId}/preparation-queue-size")
    public int getPreparationQueueSize(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("bakeryId") String bakeryId) {
        return orderService.getPreparationQueueSize(sessionId, bakeryId);
    }


    @GetMapping("/api/map/bakeries")
    public List<Bakery> getBakeryMapData(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        List<Bakery> bakeries = getOpenBakeries(sessionId);
        return bakeries;
    }


}
