package com.dropit.drop.repository;

import com.dropit.drop.entity.Drop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface DropRepository extends JpaRepository<Drop, Long>, DropRepositoryCustom {

    @Transactional(readOnly = true)
    @Query("""
            select d
            from Drop d
            join fetch d.product p
            join fetch p.seller
            where d.id = :dropId
            """)
    Optional<Drop> findDetailById(@Param("dropId") Long dropId);

    @Transactional(readOnly = true)
    @Query("""
            select d.remainingQuantity as remainingQuantity,
                   d.visible as visible
            from Drop d
            where d.id = :dropId
            """)
    Optional<DropLiveState> findLiveStateById(@Param("dropId") Long dropId);

    boolean existsByProductId(Long productId);

    Page<Drop> findAllByVisibleTrue(Pageable pageable);

    Page<Drop> findAllByProductSellerIdAndVisibleTrue(
            Long sellerId,
            Pageable pageable
    );

    Page<Drop> findAllByProductSellerId(
            Long sellerId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Drop d where d.id = :dropId")
    Optional<Drop> findByIdForUpdate(@Param("dropId") Long dropId);
    @Modifying(flushAutomatically = true)
    @Query("""
            update Drop d
            set d.remainingQuantity = d.remainingQuantity - :quantity
            where d.id = :dropId
              and :quantity > 0
              and d.remainingQuantity >= :quantity
            """)
    int decreaseStockIfAvailable(
            @Param("dropId") Long dropId,
            @Param("quantity") int quantity

    List<Drop> findAllByVisibleTrueAndOpenAtBetween(
            LocalDateTime from,
            LocalDateTime to
    );
}
