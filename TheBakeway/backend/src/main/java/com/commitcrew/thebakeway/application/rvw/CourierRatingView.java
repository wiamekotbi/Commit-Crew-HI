package com.commitcrew.thebakeway.application.rvw;

import java.time.Instant;

import com.commitcrew.thebakeway.domain.rating.CourierRating;

public record CourierRatingView(
        String ratingId,
        String orderId,
        String courierId,
        int score,
        Instant ratedAt) {

    public static CourierRatingView from(CourierRating rating) {
        return new CourierRatingView(
                rating.getId(),
                rating.getDelivery().getOrder().getId(),
                rating.getDelivery().getCourier().getCourierId(),
                rating.getScore(),
                rating.getRatedAt());
    }
}
