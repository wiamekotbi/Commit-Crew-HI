
package com.commitcrew.thebakeway.application.prc;

public record PricingConfiguration(
        double deliveryFee,
        double serviceFee,
        double smallOrderThreshold,
        double smallOrderSurcharge) {
}
