package com.dropit.order.validation;

import com.dropit.order.entity.OrderRequest;
import com.dropit.order.messaging.InvalidOrderMessageException;
import com.dropit.order.messaging.OrderMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OrderMessageValidator {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    public static void validate(OrderMessage message) {
        if (message == null
                || message.schemaVersion() != OrderMessage.CURRENT_SCHEMA_VERSION
                || message.requestId() == null
                || message.userId() == null || message.userId() <= 0
                || message.dropId() == null || message.dropId() <= 0
                || invalidHash(message.idempotencyKeyHash())
                || invalidHash(message.requestFingerprint())
                || message.quantity() <= 0
                || message.acceptedAt() == null
                || message.productName() == null || message.productName().isBlank()
                || message.productName().length() > 100
                || message.unitPrice() == null
                || message.unitPrice().signum() <= 0
                || message.unitPrice().scale() > 0
                || message.unitPrice().precision() - message.unitPrice().scale() > 13
                || message.discountRate() < 0 || message.discountRate() > 100) {
            throw new InvalidOrderMessageException("지원하지 않거나 필수 값이 없는 주문 메시지입니다.");
        }
    }

    public static void validateSameRequest(OrderRequest request, OrderMessage message) {
        if (!Objects.equals(request.getUserId(), message.userId())
                || !Objects.equals(request.getDropId(), message.dropId())
                || !Objects.equals(request.getIdempotencyKeyHash(), message.idempotencyKeyHash())
                || !Objects.equals(request.getPayloadHash(), message.requestFingerprint())
                || request.getQuantity() != message.quantity()
                || !Objects.equals(request.getAcceptedAt(), acceptedAt(message))
                || !Objects.equals(request.getProductName(), message.productName())
                || request.getUnitPrice().compareTo(message.unitPrice()) != 0
                || request.getDiscountRate() != message.discountRate()) {
            throw new InvalidOrderMessageException("동일 requestId에 서로 다른 주문 payload가 전달되었습니다.");
        }
    }

    public static LocalDateTime acceptedAt(OrderMessage message) {
        return LocalDateTime.ofInstant(
                message.acceptedAt().truncatedTo(ChronoUnit.MICROS),
                BUSINESS_ZONE
        );
    }

    private static boolean invalidHash(String value) {
        return value == null || value.isBlank() || value.length() > 64;
    }
}
