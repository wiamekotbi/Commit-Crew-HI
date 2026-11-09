package com.commitcrew.thebakeway.domain.courier;

import java.time.Instant;

import com.commitcrew.thebakeway.domain.order.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "courier_deliveries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_courier_deliveries_order", columnNames = "order_id")
})
public class CourierDelivery {

    @Id
    @GeneratedValue
    @org.hibernate.annotations.UuidGenerator
    @Column(name = "courier_delivery_id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_courier_deliveries_courier"))
    private Courier courier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_courier_deliveries_order"))
    private Order order;

    @Column(name = "delivered_at", nullable = false, updatable = false)
    private Instant deliveredAt;

    protected CourierDelivery() {
    }

    public CourierDelivery(Courier courier, Order order, Instant deliveredAt) {
        if (courier == null || order == null || deliveredAt == null) {
            throw new IllegalArgumentException("Courier, order and delivery time are required");
        }
        this.courier = courier;
        this.order = order;
        this.deliveredAt = deliveredAt;
    }

    public String getId() {
        return id;
    }

    public Courier getCourier() {
        return courier;
    }

    public Order getOrder() {
        return order;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}