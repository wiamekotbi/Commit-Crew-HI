package com.commitcrew.thebakeway.domain.order;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import com.commitcrew.thebakeway.domain.account.Role;
import com.commitcrew.thebakeway.domain.menu.MenuItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Context of the State pattern: every lifecycle action is delegated to the current OrderState
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

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    // Persisted copy of state.getStatus(); only setState(OrderState) changes it
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Transient
    private OrderState state;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "order")
    private List<OrderLine> orderItems = new ArrayList<>();

    protected Order() {}

    public Order(String customerId, String bakeryId, String recipientName, String deliveryAddress) {
        this.customerId = customerId;
        this.bakeryId = bakeryId;
        this.recipientName = recipientName;
        this.deliveryAddress = deliveryAddress;
        this.createdAt = LocalDateTime.now();
        setState(new PlacedState());
    }

    @PostLoad
    void restoreState() {
        this.state = OrderStateFactory.fromStatus(status.name());
    }

    // Only state classes call this
    void setState(OrderState state) {
        this.state = state;
        this.status = OrderStatus.valueOf(state.getStatus());
        this.updatedAt = LocalDateTime.now();
    }

    public void accept() { state.accept(this); }
    public void reject(String reason) { state.reject(this, reason); }
    public void startPreparing() { state.startPreparing(this); }
    public void markReady() { state.markReady(this); }
    public void confirmPickup() { state.confirmPickup(this); }
    public void confirmDelivery() { state.confirmDelivery(this); }
    public void cancel(Role role) { state.cancel(this, role); }

    public void addOrderItem(MenuItem menuItem, int quantity) {
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
    public String getRecipientName() { return recipientName; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getStatus() { return state.getStatus(); }
    public String getRejectionReason() { return rejectionReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<OrderLine> getOrderItems() { return orderItems; }
}
