package com.dropit.order.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

/** Consumer 내부 주문 확정 입력용 */
public record OrderFinalizationRequest(
        UUID requestId,
        Long userId,
        Long dropId,
        String idempotencyKeyHash,
        String payloadHash,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        int discountRate
) {
}
