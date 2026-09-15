package com.dropit.order.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisOrderFinalizationSyncAdapter {

    private static final DefaultRedisScript<String> SYNC_SCRIPT = createScript();

    private final StringRedisTemplate redisTemplate;

    public OrderRequestSyncResult sync(OrderRequestSyncCommand command) {
        String result = redisTemplate.execute(
                SYNC_SCRIPT,
                List.of(
                        RedisOrderKeyFactory.idempotencyKey(
                                command.dropId(),
                                command.userId(),
                command.idempotencyKeyHash()
                        ),
                        RedisOrderKeyFactory.stockKey(command.dropId()),
                        RedisOrderKeyFactory.purchaseKey(command.dropId(), command.userId())
                ),
                command.requestId().toString(),
                command.status().name(),
                Integer.toString(command.quantity()),
                command.orderId() == null ? "" : command.orderId().toString(),
                command.failureCode() == null ? "" : command.failureCode(),
                Long.toString(command.desiredVersion()),
                command.userId().toString()
        );
        if (result == null) {
            throw new IllegalStateException("Redis 주문 최종 상태 반영 결과가 없습니다.");
        }
        return OrderRequestSyncResult.valueOf(result);
    }

    private static DefaultRedisScript<String> createScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/sync-order-request.lua"));
        script.setResultType(String.class);
        return script;
    }
}
