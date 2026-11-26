
package com.commitcrew.thebakeway.application.prc;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

@Service
public class PricingService {

    private final PricingConfigurationReader configurationReader;

    public PricingService(PricingConfigurationReader configurationReader) {
        this.configurationReader = configurationReader;
    }

    public PriceBreakdown calculatePrice(Order order) {
        BigDecimal subtotal = order.getOrderItems().stream()
                .map(item -> BigDecimal.valueOf(item.getUnitPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PriceBreakdown base = calculatePrice(subtotal);
        BigDecimal discount = order.getPromoDiscountAmount() == null
                ? BigDecimal.ZERO
                : order.getPromoDiscountAmount().min(BigDecimal.valueOf(base.total()));
        BigDecimal total = BigDecimal.valueOf(base.total()).subtract(discount)
                .setScale(2, RoundingMode.HALF_UP);
        return new PriceBreakdown(base.subtotal(), base.deliveryFee(), base.serviceFee(),
                base.smallOrderSurcharge(), total.doubleValue(), discount.doubleValue());
    }

    public PriceBreakdown calculatePriceForSubtotal(double itemSubtotal) {
        if (!Double.isFinite(itemSubtotal) || itemSubtotal < 0) {
            throw new IllegalArgumentException("Items subtotal must be a finite nonnegative amount");
        }
        return calculatePrice(BigDecimal.valueOf(itemSubtotal));
    }

    private PriceBreakdown calculatePrice(BigDecimal subtotal) {
        PricingConfiguration config = configurationReader.read();
        BigDecimal deliveryFee = BigDecimal.valueOf(config.deliveryFee());
        BigDecimal serviceFee = BigDecimal.valueOf(config.serviceFee());
        BigDecimal surcharge = BigDecimal.ZERO;

        if (subtotal.compareTo(BigDecimal.valueOf(config.smallOrderThreshold())) < 0) {
            surcharge = BigDecimal.valueOf(config.smallOrderSurcharge());
        }

        BigDecimal total = subtotal.add(deliveryFee).add(serviceFee).add(surcharge)
                .setScale(2, RoundingMode.HALF_UP);

        return new PriceBreakdown(
                subtotal.doubleValue(),
                deliveryFee.doubleValue(),
                serviceFee.doubleValue(),
                surcharge.doubleValue(),
                total.doubleValue());
    }
}
