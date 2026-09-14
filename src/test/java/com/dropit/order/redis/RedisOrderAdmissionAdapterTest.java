package com.dropit.order.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisOrderAdmissionAdapterTest {

    @Test
    @DisplayName("request index와 절대 만료를 하나의 Lua 실행으로 저장한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void indexRequestAtomicallyWithExpiration() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        RedisOrderAdmissionAdapter adapter = new RedisOrderAdmissionAdapter(redisTemplate);

        adapter.indexRequest(snapshot());

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), anyList(), any(Object[].class));
        String script = scriptCaptor.getValue().getScriptAsString();
        assertAll(
                () -> assertTrue(script.contains("HSET")),
                () -> assertTrue(script.contains("PEXPIREAT")),
                () -> assertTrue(script.contains("DEL"))
        );
    }

    @Test
    @DisplayName("index TTL 설정이 확인되지 않으면 저장 실패로 처리한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectUnconfirmedIndexExpiration() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        RedisOrderAdmissionAdapter adapter = new RedisOrderAdmissionAdapter(redisTemplate);

        assertThrows(IllegalStateException.class, () -> adapter.indexRequest(snapshot()));
    }

    private ReservedOrderSnapshot snapshot() {
        return new ReservedOrderSnapshot(
                UUID.randomUUID(), 1L, 10L, "hash", "10:1", 1, Instant.now(),
                "product", new BigDecimal("1000"), 0, "UNCONFIRMED",
                com.dropit.order.entity.OrderRequestStatus.PENDING, null, null,
                Instant.now().plusSeconds(3600).toEpochMilli()
        );
    }
}
