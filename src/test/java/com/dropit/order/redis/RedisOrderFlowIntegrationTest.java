package com.dropit.order.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RedisOrderFlowIntegrationTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    @DisplayName("예약·중복·실패 복원이 Redis에서 원자적이고 멱등하게 처리된다")
    void reserveReplayAndRestoreOnce() {
        long dropId = 101L;
        long userId = 201L;
        long closeAt = Instant.now().plusSeconds(3600).toEpochMilli();
        redisTemplate.opsForHash().putAll(RedisOrderKeyFactory.saleKey(dropId), Map.of(
                "purchaseLimit", "3", "openAt", "0", "closeAt", Long.toString(closeAt),
                "productName", "product", "unitPrice", "1000", "discountRate", "0"));
        redisTemplate.opsForValue().set(RedisOrderKeyFactory.stockKey(dropId), "2");

        UUID requestId = UUID.randomUUID();
        long acceptedMicros = Instant.now().toEpochMilli() * 1_000L;
        OrderAdmissionRequest request = new OrderAdmissionRequest(
                requestId, userId, dropId, "hash", "101:1", 1, acceptedMicros);
        RedisOrderAdmissionAdapter admission = new RedisOrderAdmissionAdapter(redisTemplate);

        assertEquals(OrderAdmissionResultType.NEW, admission.reserve(request).type());
        assertEquals(OrderAdmissionResultType.REPLAY, admission.reserve(request).type());
        assertEquals("1", redisTemplate.opsForValue().get(RedisOrderKeyFactory.stockKey(dropId)));
        assertEquals("1", redisTemplate.opsForHash().get(RedisOrderKeyFactory.purchaseKey(dropId), "201"));

        RedisOrderFinalizationSyncAdapter finalization = new RedisOrderFinalizationSyncAdapter(redisTemplate);
        OrderRequestSyncCommand command = new OrderRequestSyncCommand(
                requestId, RedisOrderRequestState.FAILED, null, userId, dropId,
                "hash", 1, "INSUFFICIENT_STOCK", 1L, Instant.ofEpochMilli(closeAt + 86_400_000L));
        assertEquals(OrderRequestSyncResult.APPLIED, finalization.sync(command));
        assertEquals(OrderRequestSyncResult.ALREADY_APPLIED, finalization.sync(command));
        assertEquals("2", redisTemplate.opsForValue().get(RedisOrderKeyFactory.stockKey(dropId)));
        assertEquals("0", redisTemplate.opsForHash().get(RedisOrderKeyFactory.purchaseKey(dropId), "201"));
    }

    @Test
    @DisplayName("requestId index는 생성과 동시에 절대 만료 시각을 가진다")
    void indexHasExpiration() {
        RedisOrderAdmissionAdapter admission = new RedisOrderAdmissionAdapter(redisTemplate);
        UUID requestId = UUID.randomUUID();
        long expiresAt = Instant.now().plusSeconds(3600).toEpochMilli();
        ReservedOrderSnapshot snapshot = new ReservedOrderSnapshot(
                requestId, 1L, 1L, "hash", "1:1", 1, Instant.now(), "product",
                new BigDecimal("1000"), 0, "UNCONFIRMED",
                com.dropit.order.entity.OrderRequestStatus.PENDING, null, null, expiresAt);

        admission.indexRequest(snapshot);

        Long ttl = redisTemplate.getExpire(RedisOrderKeyFactory.requestIndexKey(requestId));
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 3600);
    }
}
