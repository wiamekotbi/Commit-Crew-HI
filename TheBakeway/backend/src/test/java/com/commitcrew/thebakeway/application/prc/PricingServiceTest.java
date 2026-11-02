
package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

class PricingServiceTest {

    private final PricingConfigurationReader reader = mock(PricingConfigurationReader.class);
    private final PricingService service = new PricingService(reader);

    @Test
    void calculatesPriceWithSurcharge() {
        Order order = mock(Order.class);

        when(order.calculateTotal()).thenReturn(15.0);
        when(reader.read()).thenReturn(new PricingConfiguration(4.0, 2.0, 20.0, 3.0));

        PriceBreakdown result = service.calculatePrice(order);

        assertEquals(15.0, result.subtotal());
        assertEquals(4.0, result.deliveryFee());
        assertEquals(2.0, result.serviceFee());
        assertEquals(3.0, result.smallOrderSurcharge());
        assertEquals(24.0, result.total());
    }

    @Test
    void calculatesPriceWithoutSurcharge() {
        Order order = mock(Order.class);

        when(order.calculateTotal()).thenReturn(25.0);
        when(reader.read()).thenReturn(new PricingConfiguration(4.0, 2.0, 20.0, 3.0));

        PriceBreakdown result = service.calculatePrice(order);

        assertEquals(0.0, result.smallOrderSurcharge());
        assertEquals(31.0, result.total());
    }

    @Test
    void doesNotApplySurchargeAtThreshold() {
        Order order = mock(Order.class);

        when(order.calculateTotal()).thenReturn(20.0);
        when(reader.read()).thenReturn(new PricingConfiguration(4.0, 2.0, 20.0, 3.0));

        PriceBreakdown result = service.calculatePrice(order);

        assertEquals(26.0, result.total());
        assertEquals(0.0, result.smallOrderSurcharge());
    }

    @Test
    void roundsTotalToTwoDecimalPlaces() {
        Order order = mock(Order.class);

        when(order.calculateTotal()).thenReturn(10.123);
        when(reader.read()).thenReturn(
                new PricingConfiguration(4.0, 2.0, 20.0, 3.0));

        PriceBreakdown result = service.calculatePrice(order);

        assertEquals(19.12, result.total());
    }
}
