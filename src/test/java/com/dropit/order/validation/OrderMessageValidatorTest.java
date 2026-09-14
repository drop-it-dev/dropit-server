package com.dropit.order.validation;

import com.dropit.order.entity.OrderRequest;
import com.dropit.order.messaging.OrderMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderMessageValidatorTest {

    @Test
    @DisplayName("acceptedAt의 나노초 차이는 MySQL 저장 정밀도에 맞춰 동일 요청으로 판단한다")
    void normalizeAcceptedAtToMicroseconds() {
        OrderMessage message = new OrderMessage(
                OrderMessage.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                1L,
                2L,
                "idempotency-key-hash",
                "2:1",
                "Limited Hoodie",
                new BigDecimal("59000"),
                1,
                20,
                Instant.parse("2026-09-13T12:34:56.123456789Z")
        );
        LocalDateTime acceptedAt = OrderMessageValidator.acceptedAt(message);
        OrderRequest request = new OrderRequest(
                message.requestId(),
                message.userId(),
                message.dropId(),
                message.idempotencyKeyHash(),
                message.requestFingerprint(),
                message.productName(),
                message.unitPrice(),
                message.quantity(),
                message.discountRate(),
                acceptedAt,
                Instant.parse("2026-09-14T12:34:56Z")
        );

        assertEquals(123_456_000, acceptedAt.getNano());
        assertDoesNotThrow(() -> OrderMessageValidator.validateSameRequest(request, message));
    }
}
