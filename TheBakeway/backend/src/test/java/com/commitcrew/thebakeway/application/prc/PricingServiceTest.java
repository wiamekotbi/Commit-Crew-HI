
package com.commitcrew.thebakeway.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemStatus;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

class PricingServiceTest {

    private final PricingConfigurationReader reader = mock(PricingConfigurationReader.class);
    private final PricingService service = new PricingService(reader);

    @Test
    void calculatesPriceWithSurcharge() {
        Order order = order(15.0);

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
        Order order = order(25.0);

        when(reader.read()).thenReturn(new PricingConfiguration(4.0, 2.0, 20.0, 3.0));

        PriceBreakdown result = service.calculatePrice(order);

        assertEquals(0.0, result.smallOrderSurcharge());
        assertEquals(31.0, result.total());
    }

    @Test
    void doesNotApplySurchargeAtThreshold() {
        Order order = order(20.0);

        when(reader.read()).thenReturn(new PricingConfiguration(4.0, 2.0, 20.0, 3.0));

        PriceBreakdown result = service.calculatePrice(order);

        assertEquals(26.0, result.total());
        assertEquals(0.0, result.smallOrderSurcharge());
    }

    @Test
    void roundsTotalToTwoDecimalPlaces() {
        Order order = order(10.123);

        when(reader.read()).thenReturn(
                new PricingConfiguration(4.0, 2.0, 20.0, 3.0));

        PriceBreakdown result = service.calculatePrice(order);

        assertEquals(19.12, result.total());
    }

    @Test
    void sumsLineAmountsUsingDecimalArithmetic() {
        Order order = new Order("customer-1", "bakery-1", "Customer", "Montreal");
        order.addOrderItem(new MenuItem("bakery-1", "Item", "", 0.1, MenuItemStatus.available), 3);
        when(reader.read()).thenReturn(new PricingConfiguration(0, 0, 0, 0));

        assertEquals(0.3, service.calculatePrice(order).total());
    }

    private Order order(double unitPrice) {
        Order order = new Order("customer-1", "bakery-1", "Customer", "Montreal");
        order.addOrderItem(new MenuItem("bakery-1", "Item", "", unitPrice, MenuItemStatus.available), 1);
        return order;
    }
}
