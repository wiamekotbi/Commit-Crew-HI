package com.commitcrew.thebakeway.domain.pricing;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.hibernate.annotations.UuidGenerator;

import com.commitcrew.thebakeway.domain.order.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "order_ledger_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_ledger_order_type", columnNames = { "order_id", "entry_type" })
})
public class OrderLedgerEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "ledger_entry_id", nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_ledger_order"))
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "item_subtotal", precision = 12, scale = 2, updatable = false)
    private BigDecimal itemSubtotal;

    @Column(name = "delivery_fee", precision = 12, scale = 2, updatable = false)
    private BigDecimal deliveryFee;

    @Column(name = "service_fee", precision = 12, scale = 2, updatable = false)
    private BigDecimal serviceFee;

    @Column(name = "small_order_surcharge", precision = 12, scale = 2, updatable = false)
    private BigDecimal smallOrderSurcharge;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_account_id", updatable = false)
    private String actorAccountId;

    @Column(updatable = false)
    private String reason;

    // R-PRC-03: the order's state when the entry was written (delivered, rejected,
    // cancelled or refunded)
    @Column(name = "final_state", updatable = false)
    private String finalState;

    protected OrderLedgerEntry() {
    }

    private OrderLedgerEntry(
            Order order,
            LedgerEntryType entryType,
            double amount,
            PriceBreakdown pricing,
            Instant occurredAt,
            String actorAccountId,
            String reason,
            String finalState) {
        if (order == null || entryType == null || !Double.isFinite(amount) || amount < 0
                || occurredAt == null || finalState == null) {
            throw new IllegalArgumentException("Invalid order ledger entry");
        }
        this.order = order;
        this.entryType = entryType;
        this.amount = money(amount);
        this.currency = "CAD";
        if (pricing != null) {
            this.itemSubtotal = money(pricing.subtotal());
            this.deliveryFee = money(pricing.deliveryFee());
            this.serviceFee = money(pricing.serviceFee());
            this.smallOrderSurcharge = money(pricing.smallOrderSurcharge());
        }
        this.occurredAt = occurredAt;
        this.actorAccountId = actorAccountId;
        this.reason = reason;
        this.finalState = finalState;
    }

    public static OrderLedgerEntry charge(Order order, PriceBreakdown pricing, Instant deliveredAt) {
        if (pricing == null) {
            throw new IllegalArgumentException("Pricing is required for a charge");
        }
        return new OrderLedgerEntry(order, LedgerEntryType.CHARGE, pricing.total(),
                pricing, deliveredAt, null, null, "delivered");
    }

    // A rejected or cancelled order is recorded with a total charge of zero (R-PRC-03)
    public static OrderLedgerEntry noCharge(Order order, String finalState, Instant closedAt) {
        return new OrderLedgerEntry(order, LedgerEntryType.CHARGE, 0,
                null, closedAt, null, null, finalState);
    }

    public static OrderLedgerEntry refund(
            Order order, double amount, Instant refundedAt, String actorAccountId, String reason) {
        return new OrderLedgerEntry(order, LedgerEntryType.REFUND, amount,
                null, refundedAt, actorAccountId, reason, "refunded");
    }

    public PriceBreakdown getPricing() {
        if (itemSubtotal == null || deliveryFee == null || serviceFee == null || smallOrderSurcharge == null) {
            return null;
        }
        return new PriceBreakdown(itemSubtotal.doubleValue(), deliveryFee.doubleValue(),
                serviceFee.doubleValue(), smallOrderSurcharge.doubleValue(), amount.doubleValue());
    }

    public String getId() { return id; }
    public Order getOrder() { return order; }
    public LedgerEntryType getEntryType() { return entryType; }
    public double getAmount() { return amount.doubleValue(); }
    public String getCurrency() { return currency; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActorAccountId() { return actorAccountId; }
    public String getReason() { return reason; }
    public String getFinalState() { return finalState; }

    private static BigDecimal money(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Ledger amounts must be finite and nonnegative");
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
}