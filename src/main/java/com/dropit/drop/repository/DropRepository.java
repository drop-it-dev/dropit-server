package com.dropit.drop.repository;

import com.dropit.drop.entity.Drop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface DropRepository extends JpaRepository<Drop, Long>, DropRepositoryCustom {

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
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
    );
}
