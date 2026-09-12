package com.dropit.order.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisOrderAdmissionAdapter {

    private static final DefaultRedisScript<String> RESERVE_ORDER_SCRIPT = createScript();

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
                            RedisOrderKeyFactory.stockKey(request.dropId()),
                            RedisOrderKeyFactory.purchaseKey(request.dropId(), request.userId()),
                            RedisOrderKeyFactory.idempotencyKey(
                                    request.dropId(),
                                    request.userId(),
                                    request.idempotencyKeyHash()
                            )
                    ),
                    request.requestId().toString(),
                    request.fingerprint(),
                    String.valueOf(request.quantity()),
                    String.valueOf(request.purchaseLimit())
            );
            return parseResult(result);
        } catch (RuntimeException exception) {
            return OrderAdmissionResult.of(OrderAdmissionResultType.NOT_READY);
        }
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
            default -> OrderAdmissionResult.of(OrderAdmissionResultType.NOT_READY);
        };
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
