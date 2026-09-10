package com.dropit.order.repository;

import com.dropit.order.entity.DropUserPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DropUserPurchaseRepository extends JpaRepository<DropUserPurchase, Long> {

    Optional<DropUserPurchase> findByDropIdAndUserId(Long dropId, Long userId);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO drop_user_purchases (
                drop_id,
                user_id,
                confirmed_quantity,
                created_at,
                updated_at
            ) VALUES (:dropId, :userId, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int createCounterIfAbsent(
            @Param("dropId") Long dropId,
            @Param("userId") Long userId
    );

    @Modifying
    @Query(value = """
            UPDATE drop_user_purchases
            SET confirmed_quantity = confirmed_quantity + :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE drop_id = :dropId
              AND user_id = :userId
              AND (:purchaseLimit = 0 OR confirmed_quantity + :quantity <= :purchaseLimit)
            """, nativeQuery = true)
    int increaseWithinLimit(
            @Param("dropId") Long dropId,
            @Param("userId") Long userId,
            @Param("quantity") int quantity,
            @Param("purchaseLimit") int purchaseLimit
    );

    @Modifying
    @Query(value = """
            UPDATE drop_user_purchases
            SET confirmed_quantity = confirmed_quantity - :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE drop_id = :dropId
              AND user_id = :userId
              AND confirmed_quantity >= :quantity
            """, nativeQuery = true)
    int decreaseConfirmedQuantity(
            @Param("dropId") Long dropId,
            @Param("userId") Long userId,
            @Param("quantity") int quantity
    );
}
