package com.dropit.drop.cache;

import com.dropit.order.redis.RedisOrderKeyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DropSaleCacheWriter {

    private static final DefaultRedisScript<Long> SCRIPT = script();
    private final StringRedisTemplate redisTemplate;

    public DropSaleCacheWriter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void apply(DropSaleSnapshot snapshot) {
        Long result = redisTemplate.execute(
                SCRIPT,
                List.of(
                        RedisOrderKeyFactory.saleKey(snapshot.dropId()),
                        RedisOrderKeyFactory.stockKey(snapshot.dropId()),
                        RedisOrderKeyFactory.admissionControlKey(snapshot.dropId())
                ),
                Long.toString(snapshot.operationVersion()),
                Long.toString(snapshot.saleVersion()),
                Boolean.toString(snapshot.visible()),
                Long.toString(snapshot.openAtEpochMillis()),
                Long.toString(snapshot.closeAtEpochMillis()),
                snapshot.unitPrice().toPlainString(),
                Integer.toString(snapshot.discountRate()),
                Integer.toString(snapshot.purchaseLimit()),
                snapshot.productName(),
                Integer.toString(snapshot.remainingQuantity()),
                Long.toString(snapshot.retentionUntilEpochMillis())
        );
        if (result == null || result < 0) {
            throw new IllegalStateException("Redis 판매 준비 결과가 올바르지 않습니다.");
        }
    }

    private static DefaultRedisScript<Long> script() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/prepare-drop-sale.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
