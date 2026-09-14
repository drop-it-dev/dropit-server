package com.dropit.order.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisOrderAdmissionAdapter {

    private static final DefaultRedisScript<String> RESERVE_ORDER_SCRIPT = createScript();
    private static final DefaultRedisScript<Long> CONFIRM_PUBLICATION_SCRIPT = confirmPublicationScript();
    private static final DefaultRedisScript<Long> INDEX_REQUEST_SCRIPT = indexRequestScript();

    private final StringRedisTemplate redisTemplate;

    private static DefaultRedisScript<String> createScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/reserve-order.lua"));
        script.setResultType(String.class);
        return script;
    }

    public OrderAdmissionResult reserve(OrderAdmissionRequest request) {
        try {
            String result = redisTemplate.execute(
                    RESERVE_ORDER_SCRIPT,
                    List.of(
                            RedisOrderKeyFactory.saleKey(request.dropId()),
                            RedisOrderKeyFactory.stockKey(request.dropId()),
                            RedisOrderKeyFactory.purchaseKey(request.dropId()),
                            RedisOrderKeyFactory.idempotencyKey(
                                    request.dropId(),
                                    request.userId(),
                                    request.idempotencyKeyHash()
                            )
                    ),
                    request.requestId().toString(),
                    request.fingerprint(),
                    String.valueOf(request.quantity()),
                    Long.toString(request.acceptedAtEpochMicros()),
                    Long.toString(request.userId()),
                    Long.toString(request.dropId()),
                    request.idempotencyKeyHash()
            );
            return parseResult(result);
        } catch (RuntimeException exception) {
            return OrderAdmissionResult.of(OrderAdmissionResultType.NOT_READY);
        }
    }

    public ReservedOrderSnapshot getSnapshot(Long dropId, Long userId, String keyHash) {
        return parseSnapshot(redisTemplate.opsForHash().entries(
                RedisOrderKeyFactory.idempotencyKey(dropId, userId, keyHash)));
    }

    public Optional<ReservedOrderSnapshot> findByRequestId(UUID requestId, Long userId) {
        Map<Object, Object> index = redisTemplate.opsForHash().entries(
                RedisOrderKeyFactory.requestIndexKey(requestId)
        );
        if (index.isEmpty() || !requestId.toString().equals(optional(index, "requestId"))
                || !userId.toString().equals(optional(index, "userId"))) {
            return Optional.empty();
        }
        String idempotencyKey = required(index, "idempotencyKey");
        Map<Object, Object> values = redisTemplate.opsForHash().entries(idempotencyKey);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        ReservedOrderSnapshot snapshot = parseSnapshot(values);
        if (!requestId.equals(snapshot.requestId()) || !userId.equals(snapshot.userId())) {
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    private ReservedOrderSnapshot parseSnapshot(Map<Object, Object> values) {
        if (values.isEmpty()) {
            throw new IllegalStateException("Redis 주문 예약 snapshot이 없습니다.");
        }
        long acceptedAtMicros = Long.parseLong(required(values, "acceptedAtEpochMicros"));
        long seconds = Math.floorDiv(acceptedAtMicros, 1_000_000L);
        long micros = Math.floorMod(acceptedAtMicros, 1_000_000L);
        return new ReservedOrderSnapshot(
                UUID.fromString(required(values, "requestId")),
                Long.valueOf(required(values, "userId")),
                Long.valueOf(required(values, "dropId")),
                required(values, "idempotencyKeyHash"),
                required(values, "fingerprint"),
                Integer.parseInt(required(values, "quantity")),
                Instant.ofEpochSecond(seconds, micros * 1_000L),
                required(values, "productName"),
                new java.math.BigDecimal(required(values, "unitPrice")),
                Integer.parseInt(required(values, "discountRate")),
                required(values, "publicationState"),
                com.dropit.order.entity.OrderRequestStatus.valueOf(required(values, "status")),
                optionalLong(values, "orderId"),
                optional(values, "failureCode"),
                Long.parseLong(required(values, "expiresAtEpochMillis"))
        );
    }

    public void confirmPublication(ReservedOrderSnapshot snapshot) {
        String key = RedisOrderKeyFactory.idempotencyKey(
                snapshot.dropId(), snapshot.userId(), snapshot.idempotencyKeyHash()
        );
        Long result = redisTemplate.execute(
                CONFIRM_PUBLICATION_SCRIPT,
                List.of(key),
                snapshot.requestId().toString()
        );
        if (result == null || result != 1L) {
            throw new IllegalStateException("Redis 주문 발행 상태를 확인할 수 없습니다.");
        }
    }

    public void indexRequest(ReservedOrderSnapshot snapshot) {
        String key = RedisOrderKeyFactory.requestIndexKey(snapshot.requestId());
        Long result = redisTemplate.execute(
                INDEX_REQUEST_SCRIPT,
                List.of(key),
                snapshot.requestId().toString(),
                snapshot.userId().toString(),
                snapshot.dropId().toString(),
                RedisOrderKeyFactory.idempotencyKey(
                        snapshot.dropId(), snapshot.userId(), snapshot.idempotencyKeyHash()),
                Long.toString(snapshot.expiresAtEpochMillis())
        );
        if (result == null || result != 1L) {
            throw new IllegalStateException("Redis 주문 요청 index를 저장할 수 없습니다.");
        }
    }

    private static String required(Map<Object, Object> values, String field) {
        String value = optional(values, field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Redis 주문 예약 필드가 없습니다: " + field);
        }
        return value;
    }

    private static String optional(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? null : value.toString();
    }

    private static Long optionalLong(Map<Object, Object> values, String field) {
        String value = optional(values, field);
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private static OrderAdmissionResult parseResult(String result) {
        if (result == null) {
            return OrderAdmissionResult.of(OrderAdmissionResultType.NOT_READY);
        }

        String[] parts = result.split("\\|", 2);
        return switch (parts[0]) {
            case "NEW" -> resultWithRequestId(parts, OrderAdmissionResultType.NEW);
            case "REPLAY" -> resultWithRequestId(parts, OrderAdmissionResultType.REPLAY);
            case "CONFLICT" -> OrderAdmissionResult.of(OrderAdmissionResultType.CONFLICT);
            case "OUT_OF_STOCK" -> OrderAdmissionResult.of(OrderAdmissionResultType.OUT_OF_STOCK);
            case "PURCHASE_LIMIT_EXCEEDED" ->
                    OrderAdmissionResult.of(OrderAdmissionResultType.PURCHASE_LIMIT_EXCEEDED);
            case "NOT_OPEN" -> OrderAdmissionResult.of(OrderAdmissionResultType.NOT_OPEN);
            default -> OrderAdmissionResult.of(OrderAdmissionResultType.NOT_READY);
        };
    }

    private static DefaultRedisScript<Long> confirmPublicationScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                if redis.call('HGET', KEYS[1], 'requestId') ~= ARGV[1] then
                    return 0
                end
                redis.call('HSET', KEYS[1], 'publicationState', 'CONFIRMED')
                return 1
                """);
        script.setResultType(Long.class);
        return script;
    }

    private static DefaultRedisScript<Long> indexRequestScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                redis.call('HSET', KEYS[1],
                        'requestId', ARGV[1],
                        'userId', ARGV[2],
                        'dropId', ARGV[3],
                        'idempotencyKey', ARGV[4])
                if redis.call('PEXPIREAT', KEYS[1], ARGV[5]) ~= 1 then
                    redis.call('DEL', KEYS[1])
                    return 0
                end
                return 1
                """);
        script.setResultType(Long.class);
        return script;
    }

    private static OrderAdmissionResult resultWithRequestId(
            String[] parts,
            OrderAdmissionResultType type
    ) {
        if (parts.length != 2) {
            return OrderAdmissionResult.of(OrderAdmissionResultType.NOT_READY);
        }

        try {
            UUID requestId = UUID.fromString(parts[1]);
            return new OrderAdmissionResult(type, requestId);
        } catch (IllegalArgumentException exception) {
            return OrderAdmissionResult.of(OrderAdmissionResultType.NOT_READY);
        }
    }
}
