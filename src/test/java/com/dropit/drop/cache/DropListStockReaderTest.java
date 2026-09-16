package com.dropit.drop.cache;

import com.dropit.drop.repository.DropRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropListStockReaderTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private DropRepository dropRepository;

    @Mock
    private DropListCacheMetrics cacheMetrics;

    @Mock
    private RedisStockCircuitBreaker circuitBreaker;

    @Mock
    private DropListStockDbFallbackCache dbFallbackCache;

    @InjectMocks
    private DropListStockReader stockReader;

    @Test
    void readsAllStocksWithSingleRedisRequest() {
        List<Long> dropIds = List.of(1L, 2L);
        when(circuitBreaker.shouldAttemptRedis()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of("{drop:1}:stock", "{drop:2}:stock")))
                .thenReturn(List.of("73", "41"));

        Map<Long, Integer> result = stockReader.getRemainingQuantities(dropIds);

        assertEquals(Map.of(1L, 73, 2L, 41), result);
        verify(dropRepository, never()).findRemainingQuantitiesByIds(dropIds);
    }

    @Test
    void fillsOnlyMissingRedisStockFromDatabase() {
        List<Long> dropIds = List.of(1L, 2L);
        when(circuitBreaker.shouldAttemptRedis()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of("{drop:1}:stock", "{drop:2}:stock")))
                .thenReturn(java.util.Arrays.asList("73", null));
        when(dropRepository.findRemainingQuantitiesByIds(List.of(2L)))
                .thenReturn(Map.of(2L, 40));

        Map<Long, Integer> result = stockReader.getRemainingQuantities(dropIds);

        assertEquals(Map.of(1L, 73, 2L, 40), result);
        verify(dropRepository).findRemainingQuantitiesByIds(List.of(2L));
    }

    @Test
    void fallsBackToDatabaseWhenRedisIsUnavailable() {
        List<Long> dropIds = List.of(1L, 2L);
        when(circuitBreaker.shouldAttemptRedis()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));
        when(dbFallbackCache.get(eq(dropIds), any())).thenAnswer(invocation -> {
            Supplier<Map<Long, Integer>> load = invocation.getArgument(1);
            return load.get();
        });
        when(dropRepository.findRemainingQuantitiesByIds(dropIds))
                .thenReturn(Map.of(1L, 70, 2L, 39));

        Map<Long, Integer> result = stockReader.getRemainingQuantities(dropIds);

        assertEquals(Map.of(1L, 70, 2L, 39), result);
        verify(dropRepository).findRemainingQuantitiesByIds(dropIds);
        verify(cacheMetrics).recordStockFallback();
        verify(cacheMetrics).recordStockDbLoad();
        verify(circuitBreaker).open();
    }

    @Test
    void skipsRedisWhenCircuitIsOpen() {
        List<Long> dropIds = List.of(1L, 2L);
        when(circuitBreaker.shouldAttemptRedis()).thenReturn(false);
        when(dbFallbackCache.get(eq(dropIds), any())).thenReturn(Map.of(1L, 70, 2L, 39));

        Map<Long, Integer> result = stockReader.getRemainingQuantities(dropIds);

        assertEquals(Map.of(1L, 70, 2L, 39), result);
        verify(redisTemplate, never()).opsForValue();
        verify(cacheMetrics).recordStockFallback();
    }
}
