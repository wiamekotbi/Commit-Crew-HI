package com.commitcrew.thebakeway.domain.rating;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRatingRepository extends JpaRepository<CourierRating, String> {

    boolean existsByDelivery_Order_Id(String orderId);

    List<CourierRating> findByCustomerAccountIdOrderByRatedAtDesc(String customerAccountId);
}
