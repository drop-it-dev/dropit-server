package com.dropit.order.repository;

import com.dropit.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByDropId(Long dropId);

    List<OrderItem> findAllByOrder_IdOrderByIdAsc(Long orderId);
}
