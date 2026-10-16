package com.commitcrew.thebakeway.domain.order;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "order_id", updatable = false, nullable = false)
    private String id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "bakery_id", nullable = false)
    private String bakeryId;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    // Only allow selected Strings to be in the db
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus state; 

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "order")
    private List<OrderLine> orderItems = new ArrayList<>();

    protected Order() {}

    public Order(String customerId, String bakeryId, String deliveryAddress, OrderStatus state) {
        this.customerId = customerId;
        this.bakeryId = bakeryId;
        this.deliveryAddress = deliveryAddress;
        this.state = state;
        this.createdAt = LocalDateTime.now();
    }

    public void addOrderItem(com.commitcrew.thebakeway.domain.menu.MenuItem menuItem, int quantity) {
        OrderLine item = new OrderLine(this, menuItem, quantity);
        this.orderItems.add(item);
    }

    public double calculateTotal() {
        double total = 0.0;
        for (OrderLine item : orderItems) {
            total += item.calculateSubtotal();
        }
        return total;
    }

    // Getters and Setters
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getBakeryId() { return bakeryId; }
    public void setBakeryId(String bakeryId) { this.bakeryId = bakeryId; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public OrderStatus getState() { return state; }
    public void setState(OrderStatus state) { this.state = state; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<OrderLine> getOrderItems() { return orderItems; }
}