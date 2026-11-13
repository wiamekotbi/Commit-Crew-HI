package com.commitcrew.thebakeway.application.dlv;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.domain.courier.CourierDeliveryRepository;

@Service
public class CourierHistoryService {

    private final CourierDeliveryRepository courierDeliveryRepository;

    public CourierHistoryService(CourierDeliveryRepository courierDeliveryRepository) {
        this.courierDeliveryRepository = courierDeliveryRepository;
    }

    // The deliveries this courier carried, newest first, without charges (R-PRC-04, UC-PRC-04 4b)
    @Transactional(readOnly = true)
    public List<CompletedDeliveryView> getPastDeliveries(String courierId) {
        return courierDeliveryRepository.findByCourier_CourierIdOrderByDeliveredAtDesc(courierId).stream()
                .map(delivery -> new CompletedDeliveryView(
                        delivery.getOrder().getId(),
                        delivery.getOrder().getBakeryId(),
                        delivery.getOrder().getDeliveryAddress(),
                        delivery.getOrder().getStatus(),
                        delivery.getDeliveredAt()))
                .toList();
    }
}
