
package com.commitcrew.thebakeway.application.prc;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

public class PricingService {

    public PriceBreakdown calculatePrice(Order order, PricingConfiguration config) {

        double subtotal = order.calculateTotal();
        double surcharge = 0;

        if (subtotal < config.smallOrderThreshold()) {
            surcharge = config.smallOrderSurcharge();
        }

        double total = subtotal + config.deliveryFee()
                + config.serviceFee() + surcharge;

        return new PriceBreakdown(
                subtotal,
                config.deliveryFee(),
                config.serviceFee(),
                surcharge,
                total);
    }
}
