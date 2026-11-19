package com.commitcrew.thebakeway.application.rvw;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.authorization.PermissionPolicy;
import com.commitcrew.thebakeway.application.ord.OrderAccessPolicy;
import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.order.OrderStatus;
import com.commitcrew.thebakeway.domain.rating.BakeryRating;
import com.commitcrew.thebakeway.domain.rating.BakeryRatingRepository;
import com.commitcrew.thebakeway.domain.rating.CourierRating;
import com.commitcrew.thebakeway.domain.rating.CourierRatingRepository;
import com.commitcrew.thebakeway.domain.session.Session;

// UC-RVW-01: the customer rates the bakery and the courier of their own delivered order in one
// submission, once per order (R-RVW-01). Both ratings are saved in one transaction, or neither.
@Service
public class OrderRatingService {

    static final String RATE_ORDER = "RATE_ORDER";

    private final SessionManager sessionManager;
    private final PermissionPolicy permissionPolicy;
    private final OrderRegistry orderRegistry;
    private final OrderAccessPolicy orderAccess;
    private final BakeryRatingRepository bakeryRatingRepository;
    private final CourierRatingRepository courierRatingRepository;
    private final CourierRatingService courierRatingService;

    public OrderRatingService(
            SessionManager sessionManager,
            PermissionPolicy permissionPolicy,
            OrderRegistry orderRegistry,
            OrderAccessPolicy orderAccess,
            BakeryRatingRepository bakeryRatingRepository,
            CourierRatingRepository courierRatingRepository,
            CourierRatingService courierRatingService) {
        this.sessionManager = sessionManager;
        this.permissionPolicy = permissionPolicy;
        this.orderRegistry = orderRegistry;
        this.orderAccess = orderAccess;
        this.bakeryRatingRepository = bakeryRatingRepository;
        this.courierRatingRepository = courierRatingRepository;
        this.courierRatingService = courierRatingService;
    }

    @Transactional
    public OrderRatingView rateOrder(String sessionId, String orderId, Integer bakeryScore, Integer courierScore) {
        Session session = requireCustomer(sessionId);
        Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);

        // 4a: another customer's order is reported as not found (OrderAccessPolicy, refactoring R1)
        orderAccess.requireCanView(session, order);

        // 5a: only delivered orders can be rated
        if (!OrderStatus.delivered.name().equals(order.getStatus())) {
            throw new IllegalStateException("Only delivered orders can be rated");
        }

        // 6a: one rating per order, for the bakery and the courier together
        if (isRated(orderId)) {
            throw alreadyRated();
        }

        // 7a: both ratings are required, each from 1 to 5
        requireScore(bakeryScore, "bakery");
        requireScore(courierScore, "courier");

        try {
            BakeryRating bakeryRating = bakeryRatingRepository.saveAndFlush(
                    new BakeryRating(order, session.getAccountId(), bakeryScore, Instant.now()));
            CourierRatingView courierRating = courierRatingService.rateCourier(sessionId, orderId, courierScore);
            return OrderRatingView.of(bakeryRating, courierRating);
        } catch (DataIntegrityViolationException exception) {
            // A concurrent request saved a rating for this order first
            throw alreadyRated();
        }
    }

    // The customer's own ratings, one row per rated order
    @Transactional(readOnly = true)
    public List<OrderRatingView> getMyRatings(String sessionId) {
        Session session = requireCustomer(sessionId);
        Map<String, CourierRating> courierRatings = courierRatingRepository
                .findByCustomerAccountIdOrderByRatedAtDesc(session.getAccountId()).stream()
                .collect(Collectors.toMap(rating -> rating.getDelivery().getOrder().getId(), Function.identity()));

        return bakeryRatingRepository.findByCustomerAccountIdOrderByRatedAtDesc(session.getAccountId()).stream()
                .map(rating -> OrderRatingView.of(rating,
                        courierRatings.containsKey(rating.getOrder().getId())
                                ? CourierRatingView.from(courierRatings.get(rating.getOrder().getId()))
                                : null))
                .toList();
    }

    private boolean isRated(String orderId) {
        return bakeryRatingRepository.existsByOrder_Id(orderId)
                || courierRatingRepository.existsByDelivery_Order_Id(orderId);
    }

    private Session requireCustomer(String sessionId) {
        Session session = sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
        if (!permissionPolicy.isAllowed(RATE_ORDER, session.getRole())) {
            throw new SecurityException("Only customers can rate their orders");
        }
        return session;
    }

    private static void requireScore(Integer score, String target) {
        if (score == null || score < 1 || score > 5) {
            throw new IllegalArgumentException("The " + target + " rating must be from 1 to 5");
        }
    }

    private static IllegalStateException alreadyRated() {
        return new IllegalStateException("This order has already been rated");
    }
}
