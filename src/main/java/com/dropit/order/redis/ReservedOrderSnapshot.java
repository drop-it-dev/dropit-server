package com.dropit.order.redis;

import com.dropit.order.entity.OrderRequestStatus;
import com.dropit.order.messaging.OrderMessage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservedOrderSnapshot(
        UUID requestId,
        Long userId,
        Long dropId,
        String idempotencyKeyHash,
        String fingerprint,
        int quantity,
        Instant acceptedAt,
        String productName,
        BigDecimal unitPrice,
        int discountRate,
        String publicationState,
        OrderRequestStatus status,
        Long orderId,
        String failureCode,
        long expiresAtEpochMillis
) {
    public OrderMessage toMessage() {
        return new OrderMessage(
                OrderMessage.CURRENT_SCHEMA_VERSION, requestId, userId, dropId,
                idempotencyKeyHash, fingerprint, productName, unitPrice,
                quantity, discountRate, acceptedAt
        );
    }

    public boolean terminal() {
        return status == OrderRequestStatus.SUCCEEDED || status == OrderRequestStatus.FAILED;
    }

    public boolean publicationConfirmed() {
        return "CONFIRMED".equals(publicationState);
    }
}
