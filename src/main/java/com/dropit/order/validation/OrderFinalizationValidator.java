package com.dropit.order.validation;

import com.dropit.global.exception.CommonErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.dto.request.OrderFinalizationRequest;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.exception.OrderErrorCode;

public final class OrderFinalizationValidator {

    private OrderFinalizationValidator() {
    }

    public static void validate(OrderFinalizationRequest input) {
        if (input == null
                || input.requestId() == null
                || input.userId() == null
                || input.dropId() == null
                || isBlank(input.idempotencyKeyHash())
                || input.idempotencyKeyHash().length() > 64
                || isBlank(input.payloadHash())
                || input.payloadHash().length() > 64
                || input.quantity() <= 0
                || isBlank(input.productName())
                || input.productName().length() > 100
                || input.unitPrice() == null
                || input.unitPrice().signum() <= 0
                || input.unitPrice().scale() > 0
                || input.unitPrice().precision() - input.unitPrice().scale() > 13
                || input.discountRate() < 0
                || input.discountRate() > 100) {
            throw new ServiceException(CommonErrorCode.INVALID_INPUT);
        }
    }

    public static void validateStoredRequest(OrderRequest request, OrderFinalizationRequest input) {
        if (!request.matches(
                input.userId(),
                input.dropId(),
                input.idempotencyKeyHash(),
                input.payloadHash(),
                input.quantity(),
                input.productName(),
                input.unitPrice(),
                input.discountRate()
        )) {
            throw new ServiceException(OrderErrorCode.ORDER_REQUEST_PAYLOAD_MISMATCH);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
