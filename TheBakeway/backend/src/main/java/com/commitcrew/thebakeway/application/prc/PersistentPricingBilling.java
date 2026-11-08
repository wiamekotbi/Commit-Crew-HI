package com.commitcrew.thebakeway.application.prc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.commitcrew.thebakeway.domain.order.Order;
import com.commitcrew.thebakeway.domain.pricing.LedgerEntryType;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerEntry;
import com.commitcrew.thebakeway.domain.pricing.OrderLedgerRepository;
import com.commitcrew.thebakeway.domain.pricing.PriceBreakdown;

@Component
public class PersistentPricingBilling implements PricingBilling {

    private final OrderLedgerRepository ledgerRepository;

    public PersistentPricingBilling(OrderLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    @Transactional
    public boolean recordChargeIfAbsent(Order order, PriceBreakdown pricing, Instant deliveredAt) {
        if (ledgerRepository.findByOrder_IdAndEntryType(order.getId(), LedgerEntryType.CHARGE).isPresent()) {
            return false;
        }
        ledgerRepository.save(OrderLedgerEntry.charge(order, pricing, deliveredAt));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Charge> findCharge(String orderId) {
        return ledgerRepository.findByOrder_IdAndEntryType(orderId, LedgerEntryType.CHARGE)
                .map(entry -> new Charge(entry.getId(), entry.getPricing()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> findRefunds(String orderId) {
        return ledgerRepository.findByOrder_IdOrderByOccurredAtAsc(orderId).stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.REFUND)
                .map(entry -> new Refund(entry.getId(), entry.getAmount(),
                        entry.getOccurredAt(), entry.getReason()))
                .toList();
    }

}