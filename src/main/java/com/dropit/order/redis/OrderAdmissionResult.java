package com.dropit.order.redis;

import java.util.UUID;

public record OrderAdmissionResult(
        OrderAdmissionResultType type,
        UUID requestId
) {

    public static OrderAdmissionResult newReservation(UUID requestId) {
        return new OrderAdmissionResult(OrderAdmissionResultType.NEW, requestId);
    }

    public static OrderAdmissionResult replay(UUID requestId) {
        return new OrderAdmissionResult(OrderAdmissionResultType.REPLAY, requestId);
    }

    public static OrderAdmissionResult of(OrderAdmissionResultType type) {
        return new OrderAdmissionResult(type, null);
    }
}
