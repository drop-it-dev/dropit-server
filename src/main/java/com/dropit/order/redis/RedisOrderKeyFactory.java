package com.dropit.order.redis;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisOrderKeyFactory {

    public static String stockKey(Long dropId) {
        return dropKey(dropId) + ":stock";
    }

    public static String saleKey(Long dropId) {
        return dropKey(dropId) + ":sale";
    }

    public static String admissionControlKey(Long dropId) {
        return dropKey(dropId) + ":admission";
    }

    public static String purchaseKey(Long dropId) {
        return dropKey(dropId) + ":purchases";
    }

    public static String purchaseKey(Long dropId, Long userId) {
        return purchaseKey(dropId);
    }

    public static String idempotencyKey(Long dropId, Long userId, String idempotencyKeyHash) {
        return dropKey(dropId) + ":idempotency:user:" + userId + ":" + idempotencyKeyHash;
    }

    private static String dropKey(Long dropId) {
        if (dropId == null || dropId <= 0) {
            throw new IllegalArgumentException("dropId must be positive");
        }
        return "{drop:" + dropId + "}";
    }
}
