package com.commitcrew.thebakeway.application.dlv;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.application.prc.OrderLedgerEntryView;
import com.commitcrew.thebakeway.domain.courier.CourierDelivery;
import com.commitcrew.thebakeway.domain.courier.CourierDeliveryRepository;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;

@Service
public class CourierHistoryService {

    private final CourierDeliveryRepository courierDeliveryRepository;
    private final OrderLedgerRepository ledgerRepository;

    public CourierHistoryService(
            CourierDeliveryRepository courierDeliveryRepository,
            OrderLedgerRepository ledgerRepository) {
        this.courierDeliveryRepository = courierDeliveryRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional(readOnly = true)
    public List<CompletedDeliveryView> getPastDeliveries(String courierId) {
        List<CourierDelivery> deliveries =
                courierDeliveryRepository.findByCourier_CourierIdOrderByDeliveredAtDesc(courierId);
        if (deliveries.isEmpty()) {
            return List.of();
        }

        List<String> orderIds = deliveries.stream()
                .map(delivery -> delivery.getOrder().getId())
                .toList();
        Map<String, List<OrderLedgerEntryView>> ledgerByOrder = ledgerRepository
                .findByOrder_IdInOrderByOccurredAtAsc(orderIds).stream()
                .map(OrderLedgerEntryView::from)
                .collect(Collectors.groupingBy(OrderLedgerEntryView::orderId));

        return deliveries.stream()
                .map(delivery -> new CompletedDeliveryView(
                        delivery.getOrder().getId(),
                        delivery.getDeliveredAt(),
                        ledgerByOrder.getOrDefault(delivery.getOrder().getId(), List.of())))
                .toList();
    }
}
