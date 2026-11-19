package com.commitcrew.thebakeway.domain.rating;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BakeryRatingRepository extends JpaRepository<BakeryRating, String> {

    boolean existsByOrder_Id(String orderId);

    List<BakeryRating> findByCustomerAccountIdOrderByRatedAtDesc(String customerAccountId);

    List<BakeryRating> findByBakeryId(String bakeryId);
}
