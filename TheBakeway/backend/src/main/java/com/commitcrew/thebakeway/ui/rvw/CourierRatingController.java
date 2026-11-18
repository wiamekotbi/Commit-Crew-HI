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
import com.commitcrew.thebakeway.application.rvw.CourierRatingService;
import com.commitcrew.thebakeway.application.rvw.CourierRatingView;

@RestController
@RequestMapping("/api")
public class CourierRatingController {

    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final CourierRatingService ratingService;

    public CourierRatingController(CourierRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/orders/{orderId}/courier-rating")
    public ResponseEntity<CourierRatingView> rateCourier(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId,
            @PathVariable String orderId,
            @RequestBody RatingRequest request) {
        int score = request == null ? 0 : request.score();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ratingService.rateCourier(sessionId, orderId, score));
    }

    @GetMapping("/courier-ratings/mine")
    public List<CourierRatingView> getMyRatings(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionId) {
        return ratingService.getMyCourierRatings(sessionId);
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

    public record RatingRequest(int score) {
    }
}
