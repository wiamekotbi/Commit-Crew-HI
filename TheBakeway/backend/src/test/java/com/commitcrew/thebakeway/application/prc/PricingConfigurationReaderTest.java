
package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PricingConfigurationReaderTest {

    @Test
    void readsPricingConfiguration() {
        PricingConfiguration config = new PricingConfigurationReader().read();

        assertEquals(4.0, config.deliveryFee());
        assertEquals(2.0, config.serviceFee());
        assertEquals(20.0, config.smallOrderThreshold());
        assertEquals(3.0, config.smallOrderSurcharge());
    }
}
