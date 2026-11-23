package com.commitcrew.thebakeway.ui.prc;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.prc.OrderBillingService;
import com.commitcrew.thebakeway.application.prc.OrderLedgerEntryView;
import com.commitcrew.thebakeway.application.prc.OrderPricingQuote;
import com.commitcrew.thebakeway.application.prc.OrderPricingService;
import com.commitcrew.thebakeway.application.prc.OrderSummary;
import com.commitcrew.thebakeway.application.prc.OrderSummaryService;

@RestController
@RequestMapping("/api/orders")
public class OrderSummaryController {

    private final OrderSummaryService orderSummaryService;
    private final OrderBillingService orderBillingService;
    private final OrderPricingService orderPricingService;

    public OrderSummaryController(
            OrderSummaryService orderSummaryService,
            OrderBillingService orderBillingService,
            OrderPricingService orderPricingService) {
        this.orderSummaryService = orderSummaryService;
        this.orderBillingService = orderBillingService;
        this.orderPricingService = orderPricingService;
    }

    @GetMapping("/pricing")
    public OrderPricingQuote quoteCart(
            @CookieValue(value = "BAKEWAY_SESSION_ID", required = false) String sessionId,
            @RequestParam("subtotal") double subtotal,
            @RequestParam(value = "promoCode", required = false) String promoCode) {
        return orderPricingService.quoteCart(sessionId, subtotal, promoCode);
    }

    @GetMapping("/{orderId}/summary")
    public OrderSummary getSummary(
            @CookieValue(value = "BAKEWAY_SESSION_ID", required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderSummaryService.getSummary(sessionId, orderId);
    }

    @GetMapping("/{orderId}/ledger")
    public List<OrderLedgerEntryView> getLedger(
            @CookieValue(value = "BAKEWAY_SESSION_ID", required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderBillingService.getLedger(sessionId, orderId);
    }

    @GetMapping("/{orderId}/pricing")
    public OrderPricingQuote quoteOrder(
            @CookieValue(value = "BAKEWAY_SESSION_ID", required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderPricingService.quoteOrder(sessionId, orderId);
    }

    @PostMapping("/{orderId}/refund")
    public OrderLedgerEntryView refund(
            @CookieValue(value = "BAKEWAY_SESSION_ID", required = false) String sessionId,
            @PathVariable("orderId") String orderId,
            @RequestBody RefundRequest request) {
        return orderBillingService.refundDeliveredOrder(sessionId, orderId,
                request == null ? null : request.reason());
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
