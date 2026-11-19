package com.commitcrew.thebakeway.domain.rating;

import java.time.Instant;

import org.hibernate.annotations.UuidGenerator;

import com.commitcrew.thebakeway.domain.order.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// The customer's rating of the bakery of one delivered order (R-RVW-01).
// One rating per order: the unique constraint refuses a second one even under concurrent requests.
@Entity
@Table(name = "bakery_ratings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bakery_ratings_order", columnNames = "order_id")
})
public class BakeryRating {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "bakery_rating_id", nullable = false, updatable = false)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_bakery_ratings_order"))
    private Order order;

    // Copied from the order so the bakery's average can be computed without loading orders (R-RVW-02)
    @Column(name = "bakery_id", nullable = false, updatable = false)
    private String bakeryId;

    @Column(name = "customer_account_id", nullable = false, updatable = false)
    private String customerAccountId;

    @Column(nullable = false, updatable = false)
    private int score;

    @Column(name = "rated_at", nullable = false, updatable = false)
    private Instant ratedAt;

    protected BakeryRating() {
    }

    public BakeryRating(Order order, String customerAccountId, int score, Instant ratedAt) {
        if (order == null || customerAccountId == null || customerAccountId.isBlank()
                || score < 1 || score > 5 || ratedAt == null) {
            throw new IllegalArgumentException("A bakery rating requires an order, customer, score from 1 to 5, and time");
        }
        this.order = order;
        this.bakeryId = order.getBakeryId();
        this.customerAccountId = customerAccountId;
        this.score = score;
        this.ratedAt = ratedAt;
    }

    public String getId() { return id; }
    public Order getOrder() { return order; }
    public String getBakeryId() { return bakeryId; }
    public String getCustomerAccountId() { return customerAccountId; }
    public int getScore() { return score; }
    public Instant getRatedAt() { return ratedAt; }
}
