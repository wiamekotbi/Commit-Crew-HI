package com.commitcrew.thebakeway.domain.courier;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierDeliveryRepository extends JpaRepository<CourierDelivery, String> {

    List<CourierDelivery> findByCourier_CourierIdOrderByDeliveredAtDesc(String courierId);
}