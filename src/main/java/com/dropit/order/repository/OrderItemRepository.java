package com.dropit.order.repository;

import com.dropit.order.entity.OrderItem;
import com.dropit.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrder_IdOrderByIdAsc(Long orderId);

    @Query("""
            select coalesce(sum(oi.quantity), 0)
            from OrderItem oi
            where oi.order.user.id = :userId
              and oi.drop.id = :dropId
              and oi.order.status = :status
            """)
    long sumQuantityByUserAndDropAndStatus(
            @Param("userId") Long userId,
            @Param("dropId") Long dropId,
            @Param("status") OrderStatus status
    );
}
