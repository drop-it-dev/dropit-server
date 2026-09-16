package com.dropit.notification.email.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 구매가 최종 확정된 뒤 이메일 발송 큐에 전달하는 메시지입니다.
 */
public record PurchaseCompletedMessage(
        int schemaVersion,
        UUID eventId,
        Long orderId,
        Long userId,
        String recipientEmail,
        String username,
        String productName,
        int quantity,
        BigDecimal totalPrice,
        Instant completedAt
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
}
