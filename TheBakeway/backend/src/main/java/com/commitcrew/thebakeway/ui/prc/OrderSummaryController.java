package com.commitcrew.thebakeway.ui.prc;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.prc.OrderSummary;
import com.commitcrew.thebakeway.application.prc.OrderSummaryService;

@RestController
@RequestMapping("/api/orders")
public class OrderSummaryController {

    private final OrderSummaryService orderSummaryService;

    public OrderSummaryController(OrderSummaryService orderSummaryService) {
        this.orderSummaryService = orderSummaryService;
    }

    @GetMapping("/{orderId}/summary")
    public OrderSummary getSummary(
            @CookieValue(value = "BAKEWAY_SESSION_ID", required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderSummaryService.getSummary(sessionId, orderId);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(OrderNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(SecurityException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStateFailure(IllegalStateException exception) {
        HttpStatus status = "Authentication required".equals(exception.getMessage())
                ? HttpStatus.UNAUTHORIZED : HttpStatus.CONFLICT;
        return error(status, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
