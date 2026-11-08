package com.commitcrew.thebakeway.application.ord;

import org.springframework.stereotype.Service;
import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.order.OrderRegistry;
import com.commitcrew.thebakeway.domain.order.OrderStatus;

import java.util.List;

@Service
public class PreparationQueue {

    private final OrderRegistry orderRegistry;

    public PreparationQueue(OrderRegistry orderRegistry) {
        this.orderRegistry = orderRegistry;
    }

    public List<Order> getPreparationQueue(String bakeryId) {
        return orderRegistry.findByBakeryIdAndStatusOrderByCreatedAtAsc(bakeryId, OrderStatus.accepted);
    }

    public int getPreparationQueueSize(String bakeryId) {
        return orderRegistry.findByBakeryIdAndStatusOrderByCreatedAtAsc(bakeryId, OrderStatus.accepted).size();
    }
}