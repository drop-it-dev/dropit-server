package com.dropit.order.repository;

import com.dropit.order.entity.OrderRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface OrderRequestRepository extends JpaRepository<OrderRequest, UUID> {

    Optional<OrderRequest> findByIdAndUserId(UUID id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderRequest r where r.order.id = :orderId")
    Optional<OrderRequest> findByOrderIdForUpdate(@Param("orderId") Long orderId);

    List<OrderRequest> findTop100ByRedisSyncPendingTrueOrderByUpdatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderRequest r where r.id = :requestId")
    Optional<OrderRequest> findByRequestIdForUpdate(@Param("requestId") UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from OrderRequest r
            where r.userId = :userId
              and r.dropId = :dropId
              and r.idempotencyKeyHash = :idempotencyKeyHash
            """)
    Optional<OrderRequest> findByUserIdAndDropIdAndIdempotencyKeyHashForUpdate(
            @Param("userId") Long userId,
            @Param("dropId") Long dropId,
            @Param("idempotencyKeyHash") String idempotencyKeyHash
    );
}
