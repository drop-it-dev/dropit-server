package com.dropit.notification.email.delivery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseEmailDeliveryRepository
        extends JpaRepository<PurchaseEmailDelivery, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from PurchaseEmailDelivery delivery where delivery.eventId = :eventId")
    Optional<PurchaseEmailDelivery> findByEventIdForUpdate(@Param("eventId") UUID eventId);
}
