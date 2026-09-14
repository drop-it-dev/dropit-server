package com.dropit.order.redis;

import java.time.Instant;
import java.util.UUID;

public record OrderRequestSyncCommand(
        UUID requestId,
        RedisOrderRequestState status,
        Long orderId,
        Long userId,
        Long dropId,
        String idempotencyKeyHash,
        int quantity,
        String failureCode,
        long desiredVersion,
        Instant expiresAt
) {
}
