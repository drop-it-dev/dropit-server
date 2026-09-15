package com.dropit.order.redis;

import java.util.UUID;

public record OrderAdmissionRequest(
        UUID requestId,
        Long userId,
        Long dropId,
        String idempotencyKeyHash,
        String fingerprint,
        int quantity,
        long acceptedAtEpochMicros
) {

    public static OrderAdmissionRequest create(
            Long userId,
            Long dropId,
            String idempotencyKeyHash,
            int quantity,
            long acceptedAtEpochMicros
    ) {
        return new OrderAdmissionRequest(
                UUID.randomUUID(),
                userId,
                dropId,
                idempotencyKeyHash,
                com.dropit.order.validation.OrderRequestFingerprint.of(dropId, quantity),
                quantity,
                acceptedAtEpochMicros
        );
    }
}
