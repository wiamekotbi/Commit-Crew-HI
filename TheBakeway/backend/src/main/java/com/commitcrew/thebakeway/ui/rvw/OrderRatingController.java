package com.commitcrew.thebakeway.ui.rvw;

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
import org.springframework.web.bind.annotation.RestController;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.rvw.OrderRatingService;
import com.commitcrew.thebakeway.application.rvw.OrderRatingView;

// UC-RVW-01: one submission rates the bakery and the courier of a delivered order
@RestController
@RequestMapping("/api")
public class OrderRatingController {

    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final OrderRatingService ratingService;

    public OrderRatingController(OrderRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/orders/{orderId}/rating")
    public ResponseEntity<OrderRatingView> rateOrder(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable String orderId,
            @RequestBody(required = false) RatingRequest request) {
        Integer bakeryScore = request == null ? null : request.bakeryScore();
        Integer courierScore = request == null ? null : request.courierScore();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ratingService.rateOrder(sessionId, orderId, bakeryScore, courierScore));
    }

    @GetMapping("/order-ratings/mine")
    public List<OrderRatingView> getMyRatings(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        return ratingService.getMyRatings(sessionId);
    }

    @GetMapping("/order-ratings/{bakeryId}/average-rating")
    public double getBakeryAvgRating(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable String bakeryId) {
        return ratingService.getBakeryAvgRating(sessionId, bakeryId);
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

    public record RatingRequest(Integer bakeryScore, Integer courierScore) {
    }
}
