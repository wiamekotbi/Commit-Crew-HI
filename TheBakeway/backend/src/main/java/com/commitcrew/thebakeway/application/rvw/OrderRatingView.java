package com.commitcrew.thebakeway.application.rvw;

import java.time.Instant;

import com.commitcrew.thebakeway.domain.rating.BakeryRating;

// One rated order: the bakery rating and the courier rating submitted together (UC-RVW-01)
public record OrderRatingView(
        String orderId,
        String bakeryId,
        int bakeryScore,
        String courierId,
        Integer courierScore,
        Instant ratedAt) {

    static OrderRatingView of(BakeryRating bakeryRating, CourierRatingView courierRating) {
        return new OrderRatingView(
                bakeryRating.getOrder().getId(),
                bakeryRating.getBakeryId(),
                bakeryRating.getScore(),
                courierRating == null ? null : courierRating.courierId(),
                courierRating == null ? null : courierRating.score(),
                bakeryRating.getRatedAt());
    }
}
