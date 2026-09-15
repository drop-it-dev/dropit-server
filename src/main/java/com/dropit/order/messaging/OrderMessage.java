package com.dropit.order.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderMessage(
        int schemaVersion,
        UUID requestId,
        Long userId,
        Long dropId,
        String idempotencyKeyHash,
        String requestFingerprint,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        int discountRate,
        Instant acceptedAt
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
