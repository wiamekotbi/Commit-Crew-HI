package com.commitcrew.thebakeway.domain.courier;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRegistry extends JpaRepository<Courier, String> {

    List<Courier> findByStatus(CourierStatus status);

    Optional<Courier> findByCurrentOrderId(String orderId);

    Optional<Courier> findByStalledOrderId(String orderId);

    /** Operator dispatch uses this to expose only explicitly stalled work. */
    List<Courier> findByStalledOrderIdIsNotNull();
}
