package com.dropit.order.dto.response;

import com.dropit.order.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long dropId,
        String productName,
        BigDecimal unitPrice,
        int discountRate,
        int quantity,
        BigDecimal itemTotalPrice
) {
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getDrop().getId(),
                orderItem.getProductName(),
                orderItem.getUnitPrice(),
                orderItem.getDiscountRate(),
                orderItem.getQuantity(),
                orderItem.getItemTotalPrice()
        );
    }
}
