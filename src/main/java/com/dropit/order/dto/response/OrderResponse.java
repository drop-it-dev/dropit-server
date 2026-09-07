package com.dropit.order.dto.response;

import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderItem;
import com.dropit.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalPrice,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order, List<OrderItem> orderItems) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                orderItems.stream().map(OrderItemResponse::from).toList()
        );
    }
}
