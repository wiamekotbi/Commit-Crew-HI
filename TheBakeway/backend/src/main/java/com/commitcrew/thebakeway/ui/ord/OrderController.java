package com.commitcrew.thebakeway.ui.ord;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.ord.BakeryNotFoundException;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.ord.OrderService;
import com.commitcrew.thebakeway.application.ord.OrderTransitionResult;
import com.commitcrew.thebakeway.application.ord.OrderView;
import com.commitcrew.thebakeway.application.ord.PlaceOrderRequest;
import com.commitcrew.thebakeway.domain.order.IllegalTransitionException;

// Order commands and queries (UC-ORD-03 to UC-ORD-08, UC-ORD-12, UC-ORD-13).
// Every call goes through the OrderServiceProxy, which checks the session and the role.
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // Must match the cookie issued by LoginController.
    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final OrderService orderService;

    // Spring injects the @Primary OrderServiceProxy.
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderTransitionResult> placeOrder(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(sessionId, request));
    }

    @PatchMapping("/{orderId}/accept")
    public OrderTransitionResult acceptOrder(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderService.acceptOrder(sessionId, orderId);
    }

    @PatchMapping("/{orderId}/reject")
    public OrderTransitionResult rejectOrder(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("orderId") String orderId,
            @RequestBody RejectOrderRequest request) {
        return orderService.rejectOrder(sessionId, orderId, request.reason());
    }

    @PatchMapping("/{orderId}/start-preparing")
    public OrderTransitionResult startPreparing(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderService.startPreparing(sessionId, orderId);
    }

    @PatchMapping("/{orderId}/ready")
    public OrderTransitionResult markReady(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderService.markReady(sessionId, orderId);
    }

    @PatchMapping("/{orderId}/cancel")
    public OrderTransitionResult cancelOrder(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderService.cancelOrder(sessionId, orderId);
    }

    @GetMapping("/mine")
    public List<OrderView> myOrders(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        return orderService.listOrdersForCustomer(sessionId);
    }

    @GetMapping("/bakery")
    public List<OrderView> bakeryOrders(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        return orderService.listOrdersForBakery(sessionId);
    }

    @GetMapping
    public List<OrderView> allOrders(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @RequestParam(value = "bakeryId", required = false) String bakeryId,
            @RequestParam(value = "status", required = false) String status) {
        return orderService.listAllOrders(sessionId, bakeryId, status);
    }

    @GetMapping("/{orderId}")
    public OrderView getOrder(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable("orderId") String orderId) {
        return orderService.getOrder(sessionId, orderId);
    }

    @ExceptionHandler({OrderNotFoundException.class, BakeryNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    // A transition the order's current state does not allow (R-ORD-04)
    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<Map<String, String>> handleIllegalTransition(IllegalTransitionException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(SecurityException exception) {
        return error(HttpStatus.FORBIDDEN, "Access denied: your role cannot do this");
    }

    // The proxy reports a missing session with this exact message. Other state failures are refused
    // actions: a paused or closed bakery, a sold-out item, or an order that is not first in the queue.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStateFailure(IllegalStateException exception) {
        if ("Authentication required".equals(exception.getMessage())) {
            return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
        }
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
