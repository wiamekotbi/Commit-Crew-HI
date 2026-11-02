
package com.commitcrew.thebakeway.application.prc;

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
        PricingConfiguration config = configurationReader.read();

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
