package com.commitcrew.thebakeway.application.dlv;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.courier.Courier;
import com.commitcrew.thebakeway.domain.courier.CourierDelivery;
import com.commitcrew.thebakeway.domain.courier.CourierDeliveryRepository;
import com.commitcrew.thebakeway.domain.order.Order;

@Service
public class CourierDeliveryRecorder {

    private final CourierDeliveryRepository courierDeliveryRepository;

    public CourierDeliveryRecorder(CourierDeliveryRepository courierDeliveryRepository) {
        this.courierDeliveryRepository = courierDeliveryRepository;
    }

    public CourierDelivery record(Courier courier, Order order, Instant deliveredAt) {
        return courierDeliveryRepository.save(new CourierDelivery(courier, order, deliveredAt));
    }
}