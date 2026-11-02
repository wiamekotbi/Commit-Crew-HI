
package com.commitcrew.thebakeway.application.prc;

import com.commitcrew.thebakeway.domain.order.Order;

public class PricingService {

    public double calculateTotal(Order order, double deliveryFee,
            double serviceFee, double smallOrderThreshold,
            double smallOrderSurcharge) {

        double subtotal = order.calculateTotal();
        double surcharge = 0;

        if (subtotal < smallOrderThreshold) {
            surcharge = smallOrderSurcharge;
        }

        return subtotal + deliveryFee + serviceFee + surcharge;
    }
}
