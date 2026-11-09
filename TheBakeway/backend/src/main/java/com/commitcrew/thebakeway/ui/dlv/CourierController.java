package com.commitcrew.thebakeway.ui.dlv;

import java.util.Map;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;

import com.commitcrew.thebakeway.application.dlv.CourierService;
import com.commitcrew.thebakeway.application.dlv.CourierView;
import com.commitcrew.thebakeway.application.dlv.CompletedDeliveryView;
import com.commitcrew.thebakeway.application.dlv.CourierHistoryService;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.session.Session;

@RestController
@RequestMapping("/api/couriers")
public class CourierController {

    private static final String SESSION_COOKIE = "BAKEWAY_SESSION_ID";

    private final CourierService courierService;
    private final CourierHistoryService courierHistoryService;
    private final SessionManager sessionManager;
    private final PermissionPolicy permissionPolicy;

    public CourierController(
            CourierService courierService,
            SessionManager sessionManager,
            PermissionPolicy permissionPolicy) {
        this.courierService = courierService;
        this.courierHistoryService = courierHistoryService;
        this.sessionManager = sessionManager;
        this.permissionPolicy = permissionPolicy;
    }

    @GetMapping("/me")
    public CourierView getCurrentCourier(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId) {
        return courierService.getCourier(courierId(sessionId));
    }

    @GetMapping("/me/past-deliveries")
    public List<CompletedDeliveryView> getPastDeliveries(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId) {
        return courierHistoryService.getPastDeliveries(courierId(sessionId));
    }

    @PatchMapping("/me/on-duty")
    public CourierView goOnDuty(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId) {
        return courierService.goOnDuty(courierId(sessionId));
    }

    @PatchMapping("/me/off-duty")
    public CourierView goOffDuty(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId) {
        return courierService.goOffDuty(courierId(sessionId));
    }

    @PatchMapping("/offers/{offerId}/accept")
    public CourierView acceptOffer(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId,
            @PathVariable String offerId) {
        String courierId = courierId(sessionId);
        courierService.acceptOffer(courierId, offerId);
        return courierService.getCourier(courierId);
    }

    @PatchMapping("/offers/{offerId}/decline")
    public CourierView declineOffer(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId,
            @PathVariable String offerId) {
        String courierId = courierId(sessionId);
        courierService.declineOffer(courierId, offerId);
        return courierService.getCourier(courierId);
    }

    private String courierId(String sessionId) {
        Session session = sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authentication required"));
        if (session.getRole() != Role.COURIER) {
            throw new SecurityException(
                    "Only couriers can use courier commands");
        }
        return session.getAccountId();
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(
            SecurityException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
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

    // for dashboard map
    @GetMapping("/map")
    public List<Courier> getAllCouriers(
            @CookieValue(value = SESSION_COOKIE, required = false)
                    String sessionId) {
        Session session = sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authentication required"));
        if (!permissionPolicy.isAllowed("GET_COURIER_MAP_DATA", session.getRole())) {
            throw new SecurityException(
                    "Only operators may manage stalled dispatch assignments");
        }
        return courierService.getAllCouriers();
    }
}
