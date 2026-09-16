package com.dropit.notification.email.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import java.util.UUID;

public interface PurchaseEmailOutboxRepository extends JpaRepository<PurchaseEmailOutbox, UUID> {

    @Query("""
            select outbox.eventId
            from PurchaseEmailOutbox outbox
            where (outbox.status = com.dropit.notification.email.outbox.EmailOutboxStatus.PENDING
                    and outbox.nextAttemptAt <= :now)
               or (outbox.status = com.dropit.notification.email.outbox.EmailOutboxStatus.PROCESSING
                    and outbox.claimExpiresAt <= :now)
            order by outbox.createdAt asc
            """)
    List<UUID> findPublishableEventIds(
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select outbox from PurchaseEmailOutbox outbox where outbox.eventId = :eventId")
    Optional<PurchaseEmailOutbox> findByEventIdForUpdate(@Param("eventId") UUID eventId);
}
