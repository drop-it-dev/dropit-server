package com.dropit.order.redis;

import com.dropit.order.entity.OrderRequestStatus;

import java.util.UUID;

public record OrderRequestSyncCommand(
        UUID requestId,
        OrderRequestStatus status,
        Long orderId,
        Long userId,
        Long dropId,
        String idempotencyKeyHash,
        int quantity,
        String failureCode,
        long desiredVersion
) {
}
