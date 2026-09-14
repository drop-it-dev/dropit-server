package com.dropit.order.repository;

import com.dropit.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByDropId(Long dropId);

    List<OrderItem> findAllByOrder_IdOrderByIdAsc(Long orderId);

    @Query("""
            select i from OrderItem i
            where i.order.id = :orderId
            order by i.drop.id asc, i.id asc
            """)
    List<OrderItem> findAllForCancellationOrderByDropIdAsc(@Param("orderId") Long orderId);
}
