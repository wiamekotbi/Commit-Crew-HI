package com.commitcrew.thebakeway.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.commitcrew.thebakeway.domain.courier.CourierDelivery;
import com.commitcrew.thebakeway.domain.courier.CourierDeliveryRepository;
import com.commitcrew.thebakeway.domain.order.Order;

class CourierHistoryServiceTest {

    private final CourierDeliveryRepository deliveries = mock(CourierDeliveryRepository.class);
    private final CourierHistoryService service = new CourierHistoryService(deliveries);

    @Test
    void listsTheCarriedDeliveriesWithoutCharges() {
        Instant deliveredAt = Instant.parse("2026-11-07T12:00:00Z");
        Order order = mock(Order.class);
        when(order.getId()).thenReturn("order-1");
        when(order.getBakeryId()).thenReturn("bakery-1");
        when(order.getDeliveryAddress()).thenReturn("123 Main Street");
        when(order.getStatus()).thenReturn("delivered");
        CourierDelivery delivery = mock(CourierDelivery.class);
        when(delivery.getOrder()).thenReturn(order);
        when(delivery.getDeliveredAt()).thenReturn(deliveredAt);
        when(deliveries.findByCourier_CourierIdOrderByDeliveredAtDesc("courier-1"))
                .thenReturn(List.of(delivery));

        List<CompletedDeliveryView> result = service.getPastDeliveries("courier-1");

        assertEquals(List.of(new CompletedDeliveryView(
                "order-1", "bakery-1", "123 Main Street", "delivered", deliveredAt)), result);
    }

    @Test
    void returnsAnEmptyListWhenTheCourierHasNoPastDeliveries() {
        when(deliveries.findByCourier_CourierIdOrderByDeliveredAtDesc("courier-1"))
                .thenReturn(List.of());

        assertEquals(List.of(), service.getPastDeliveries("courier-1"));
    }
}
