package com.commitcrew.thebakeway.application.rvw;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.ord.OrderNotFoundException;
import com.commitcrew.thebakeway.application.reg.SessionManager;
import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.courier.CourierDelivery;
import com.commitcrew.thebakeway.domain.courier.CourierDeliveryRepository;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.order.OrderStatus;
import com.commitcrew.thebakeway.domain.rating.CourierRating;
import com.commitcrew.thebakeway.domain.rating.CourierRatingRepository;
import com.commitcrew.thebakeway.domain.session.Session;

@Service
public class CourierRatingService {

    private final SessionManager sessionManager;
    private final OrderRegistry orderRegistry;
    private final CourierDeliveryRepository deliveryRepository;
    private final CourierRatingRepository ratingRepository;

    public CourierRatingService(
            SessionManager sessionManager,
            OrderRegistry orderRegistry,
            CourierDeliveryRepository deliveryRepository,
            CourierRatingRepository ratingRepository) {
        this.sessionManager = sessionManager;
        this.orderRegistry = orderRegistry;
        this.deliveryRepository = deliveryRepository;
        this.ratingRepository = ratingRepository;
    }

    @Transactional
    public CourierRatingView rateCourier(String sessionId, String orderId, int score) {
        Session session = requireCustomer(sessionId);
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("Courier rating must be between 1 and 5");
        }

        Order order = orderRegistry.findById(orderId).orElseThrow(OrderNotFoundException::new);
        if (!session.getAccountId().equals(order.getCustomerId())) {
            throw new SecurityException("Customers can rate only couriers who delivered their own orders");
        }
        if (!OrderStatus.delivered.name().equals(order.getStatus())) {
            throw new IllegalStateException("Courier ratings are allowed only for delivered orders");
        }
        if (ratingRepository.existsByDelivery_Order_Id(orderId)) {
            throw duplicateRating();
        }

        CourierDelivery delivery = deliveryRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Delivered order has no recorded courier"));
        try {
            CourierRating saved = ratingRepository.saveAndFlush(
                    new CourierRating(delivery, session.getAccountId(), score, Instant.now()));
            return CourierRatingView.from(saved);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateRating();
        }
    }

    @Transactional(readOnly = true)
    public List<CourierRatingView> getMyCourierRatings(String sessionId) {
        Session session = requireCustomer(sessionId);
        return ratingRepository
                .findByCustomerAccountIdOrderByRatedAtDesc(session.getAccountId())
                .stream()
                .map(CourierRatingView::from)
                .toList();
    }

    private Session requireCustomer(String sessionId) {
        Session session = sessionManager.findSession(sessionId)
                .orElseThrow(() -> new IllegalStateException("Authentication required"));
        if (session.getRole() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can rate couriers");
        }
        return session;
    }

    private IllegalStateException duplicateRating() {
        return new IllegalStateException("The courier for this order has already been rated");
    }
}
