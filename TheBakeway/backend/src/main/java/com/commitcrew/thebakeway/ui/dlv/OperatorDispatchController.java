package com.commitcrew.thebakeway.ui.dlv;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.dlv.DispatchReassignmentResult;
import com.commitcrew.thebakeway.application.dlv.OperatorDispatchService;
import com.commitcrew.thebakeway.application.dlv.WaitingOrderView;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;

/**
 * HTTP boundary for the operator's dispatch waiting list and reassignment
 * command. Role authorization and eligibility decisions remain in the
 * application layer rather than trusting access to these URLs.
 */
@RestController
@RequestMapping("/api/dispatch")
public class OperatorDispatchController {

    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final OperatorDispatchService operatorDispatchService;

    public OperatorDispatchController(
            OperatorDispatchService operatorDispatchService) {
        this.operatorDispatchService = operatorDispatchService;
    }

    @GetMapping("/waiting-orders")
    public List<WaitingOrderView> getWaitingOrders(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId) {
        return operatorDispatchService.getWaitingOrders(sessionId);
    }

    @PatchMapping("/waiting-orders/{orderId}/reassign")
    public DispatchReassignmentResult reassignStalledOrder(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId,
            @PathVariable String orderId) {
        return operatorDispatchService.reassignStalledOrder(
                sessionId, orderId);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMissingOrder(
            OrderNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(
            SecurityException exception) {
        // Keep internal policy details out of an unauthorized response.
        return error(HttpStatus.FORBIDDEN,
                "Access denied for dispatch management");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(
            IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStateFailure(
            IllegalStateException exception) {
        if ("Authentication required".equals(exception.getMessage())) {
            return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
        }
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("error", message));
    }
}
