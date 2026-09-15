package com.dropit.order.dto.response;

import com.dropit.order.entity.OrderRequestStatus;
import com.dropit.order.entity.OrderStatus;

import java.util.UUID;

public record OrderRequestStatusResponse(
        UUID requestId,
        OrderRequestStatus status,
        Long orderId,
        OrderStatus orderStatus,
        String failureCode
) {
}
