package com.dropit.order.service;

import com.dropit.drop.exception.DropErrorCode;
import com.dropit.global.exception.CommonErrorCode;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.request.OrderItemCreateRequest;
import com.dropit.order.exception.OrderErrorCode;
import com.dropit.order.messaging.OrderMessagePublisher;
import com.dropit.order.redis.OrderAdmissionRequest;
import com.dropit.order.redis.OrderAdmissionResult;
import com.dropit.order.redis.RedisOrderAdmissionAdapter;
import com.dropit.order.redis.ReservedOrderSnapshot;
import com.dropit.order.validation.OrderAdmissionValidator;
import com.dropit.order.validation.OrderIdempotencyKeyHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class OrderAdmissionService {

    private final RedisOrderAdmissionAdapter redisAdapter;
    private final OrderMessagePublisher messagePublisher;

    public ReservedOrderSnapshot admit(Long userId, String rawIdempotencyKey, OrderCreateRequest request) {
        validateInput(rawIdempotencyKey, request);
        OrderItemCreateRequest item = request.items().getFirst();
        String keyHash = OrderIdempotencyKeyHasher.sha256(rawIdempotencyKey);
        Instant acceptedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long acceptedAtMicros = Math.addExact(
                Math.multiplyExact(acceptedAt.getEpochSecond(), 1_000_000L),
                acceptedAt.getNano() / 1_000L
        );

        OrderAdmissionRequest command = OrderAdmissionRequest.create(
                userId, item.dropId(), keyHash, item.quantity(), acceptedAtMicros
        );
        OrderAdmissionResult result = reserve(command);
        validateResult(result);

        ReservedOrderSnapshot snapshot;
        try {
            snapshot = redisAdapter.getSnapshot(item.dropId(), userId, keyHash);
            redisAdapter.indexRequest(snapshot);
        } catch (RuntimeException exception) {
            throw new ServiceException(OrderErrorCode.ORDER_ADMISSION_NOT_READY, exception);
        }

        if (snapshot.terminal() || snapshot.publicationConfirmed()) {
            return snapshot;
        }
        try {
            messagePublisher.publish(snapshot.toMessage());
        } catch (RuntimeException exception) {
            throw new OrderPublicationUnconfirmedException(snapshot.requestId(), exception);
        }
        try {
            redisAdapter.confirmPublication(snapshot);
        } catch (RuntimeException ignored) {
            // SQS가 이미 수락했으므로 접수 성공을 반환한다. replay는 같은 requestId로 안전하게 재발행할 수 있다.
        }
        return snapshot;
    }

    public OrderAdmissionResult reserve(OrderAdmissionRequest request) {
        OrderAdmissionValidator.validate(request);
        return redisAdapter.reserve(request);
    }

    public OrderAdmissionResult reserve(Long userId, Long dropId, String keyHash, int quantity, int ignoredLimit) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        long micros = now.getEpochSecond() * 1_000_000L + now.getNano() / 1_000L;
        return reserve(OrderAdmissionRequest.create(userId, dropId, keyHash, quantity, micros));
    }

    private void validateInput(String key, OrderCreateRequest request) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ServiceException(OrderErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        if (request == null || request.items() == null || request.items().size() != 1) {
            throw new ServiceException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private void validateResult(OrderAdmissionResult result) {
        switch (result.type()) {
            case NEW, REPLAY -> { }
            case CONFLICT -> throw new ServiceException(OrderErrorCode.IDEMPOTENCY_KEY_REUSED);
            case OUT_OF_STOCK -> throw new ServiceException(DropErrorCode.INSUFFICIENT_STOCK);
            case PURCHASE_LIMIT_EXCEEDED -> throw new ServiceException(OrderErrorCode.PURCHASE_LIMIT_EXCEEDED);
            case NOT_OPEN -> throw new ServiceException(DropErrorCode.DROP_NOT_OPEN);
            case NOT_READY -> throw new ServiceException(OrderErrorCode.ORDER_ADMISSION_NOT_READY);
        }
    }
}
