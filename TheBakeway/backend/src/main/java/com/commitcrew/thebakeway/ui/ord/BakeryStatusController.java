package com.commitcrew.thebakeway.ui.ord;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.ord.BakeryNotFoundException;
import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.domain.bakery.Bakery;

/**
 * HTTP boundary for bakery operational-status commands. The controller does
 * not decide ownership or role permissions; it forwards the session and target
 * through the Protection Proxy and application access service.
 */
@RestController
@RequestMapping("/api/bakeries")
public class BakeryStatusController {

    // Must remain consistent with the cookie issued by LoginController.
    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final OrderService orderService;

    /** Spring injects the @Primary OrderServiceProxy for protected operations. */
    public BakeryStatusController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Requests a temporary pause. Both restaurant staff and operators can
     * reach this operation, but restaurant ownership is verified downstream.
     */
    @PatchMapping("/{bakeryId}/pause")
    public Bakery pauseBakery(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("bakeryId") String bakeryId) {
        // The path identifies the requested target. Restaurant ownership is
        // still verified from authenticated account data inside the application layer.
        return orderService.pauseBakery(sessionId, bakeryId);
    }

    /**
     * Requests that a paused bakery return to open. The shared permission
     * policy reserves this operation for operators.
     */
    @PatchMapping("/{bakeryId}/reopen")
    public Bakery reopenBakery(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("bakeryId") String bakeryId) {
        // PermissionPolicy and BakeryStatusAccessService both enforce that
        // reopening is an operator-only operation.
        return orderService.reopenBakery(sessionId, bakeryId);
    }

    /** A well-formed request for an unknown bakery targets a missing resource. */
    @ExceptionHandler(BakeryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleBakeryNotFound(
            BakeryNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    /** Hides internal authorization details while returning the correct HTTP status. */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(
            SecurityException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied for bakery status management"));
    }

    /**
     * Only the proxy's exact authentication failure maps to 401. Other state
     * failures describe invalid bakery transitions and map to 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStateFailure(
            IllegalStateException exception) {
        if ("Authentication required".equals(exception.getMessage())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", exception.getMessage()));
    }
}
