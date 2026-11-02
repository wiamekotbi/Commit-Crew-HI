
package com.commitcrew.thebakeway.application.prc;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

public class PricingService {

    public PriceBreakdown calculatePrice(Order order, double deliveryFee,
            double serviceFee, double smallOrderThreshold,
            double smallOrderSurcharge) {

        double subtotal = order.calculateTotal();
        double surcharge = 0;

        if (subtotal < smallOrderThreshold) {
            surcharge = smallOrderSurcharge;
        }

        double total = subtotal + deliveryFee + serviceFee + surcharge;

        return new PriceBreakdown(subtotal, deliveryFee, serviceFee, surcharge, total);
    }
}
