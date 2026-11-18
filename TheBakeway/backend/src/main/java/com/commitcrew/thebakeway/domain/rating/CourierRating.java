package com.commitcrew.thebakeway.domain.rating;

import java.time.Instant;

import org.hibernate.annotations.UuidGenerator;

import com.commitcrew.thebakeway.domain.courier.CourierDelivery;

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

@Entity
@Table(name = "courier_ratings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_courier_ratings_delivery", columnNames = "courier_delivery_id")
})
public class CourierRating {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "courier_rating_id", nullable = false, updatable = false)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_delivery_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_courier_ratings_delivery"))
    private CourierDelivery delivery;

    @Column(name = "customer_account_id", nullable = false, updatable = false)
    private String customerAccountId;

    @Column(nullable = false, updatable = false)
    private int score;

    @Column(name = "rated_at", nullable = false, updatable = false)
    private Instant ratedAt;

    protected CourierRating() {
    }

    public CourierRating(
            CourierDelivery delivery, String customerAccountId, int score, Instant ratedAt) {
        if (delivery == null || customerAccountId == null || customerAccountId.isBlank()
                || score < 1 || score > 5 || ratedAt == null) {
            throw new IllegalArgumentException("A courier rating requires a delivery, customer, score from 1 to 5, and time");
        }
        this.delivery = delivery;
        this.customerAccountId = customerAccountId;
        this.score = score;
        this.ratedAt = ratedAt;
    }

    public String getId() { return id; }
    public CourierDelivery getDelivery() { return delivery; }
    public String getCustomerAccountId() { return customerAccountId; }
    public int getScore() { return score; }
    public Instant getRatedAt() { return ratedAt; }
}
