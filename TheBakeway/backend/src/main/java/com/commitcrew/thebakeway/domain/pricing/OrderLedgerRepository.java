package com.commitcrew.thebakeway.domain.pricing;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderLedgerRepository extends JpaRepository<OrderLedgerEntry, String> {

    Optional<OrderLedgerEntry> findByOrder_IdAndEntryType(String orderId, LedgerEntryType entryType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OrderLedgerEntry e where e.order.id = :orderId and e.entryType = :entryType")
    Optional<OrderLedgerEntry> findByOrderIdAndEntryTypeForUpdate(
            @Param("orderId") String orderId, @Param("entryType") LedgerEntryType entryType);

    List<OrderLedgerEntry> findByOrder_IdOrderByOccurredAtAsc(String orderId);
}