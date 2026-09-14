package com.dropit.order.validation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderRequestFingerprint {

    public static String of(Long dropId, int quantity) {
        if (dropId == null || dropId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("dropId and quantity must be positive");
        }
        return dropId + ":" + quantity;
    }
}
