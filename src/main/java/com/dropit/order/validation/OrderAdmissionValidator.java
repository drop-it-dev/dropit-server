package com.dropit.order.validation;

import com.dropit.global.exception.CommonErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.redis.OrderAdmissionRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderAdmissionValidator {

    public static void validate(OrderAdmissionRequest request) {
        if (request == null
                || request.requestId() == null
                || request.userId() == null
                || request.userId() <= 0
                || request.dropId() == null
                || request.dropId() <= 0
                || request.idempotencyKeyHash() == null
                || request.idempotencyKeyHash().isBlank()
                || request.idempotencyKeyHash().length() > 64
                || request.fingerprint() == null
                || !request.fingerprint().equals(
                OrderAdmissionRequest.fingerprintOf(request.dropId(), request.quantity())
        )
                || request.quantity() <= 0
                || request.purchaseLimit() < 0) {
            throw new ServiceException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
