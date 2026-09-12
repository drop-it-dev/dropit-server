package com.dropit.order.redis;

import java.util.UUID;

public record OrderAdmissionRequest(
        UUID requestId,
        Long userId,
        Long dropId,
        String idempotencyKeyHash,
        String fingerprint,
        int quantity,
        int purchaseLimit
) {

    public static OrderAdmissionRequest create(
            Long userId,
            Long dropId,
            String idempotencyKeyHash,
            int quantity,
            int purchaseLimit
    ) {
        return new OrderAdmissionRequest(
                UUID.randomUUID(),
                userId,
                dropId,
                idempotencyKeyHash,
                fingerprintOf(dropId, quantity),
                quantity,
                purchaseLimit
        );
    }

    public static String fingerprintOf(Long dropId, int quantity) {
        return dropId + ":" + quantity;
    }
}
