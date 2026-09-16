package com.dropit.global.config;

import com.dropit.drop.cache.CacheReadFailureContext;
import com.dropit.drop.cache.RedisCacheCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DropCacheErrorHandlerTest {

    private final CacheReadFailureContext readFailureContext = new CacheReadFailureContext();
    private final RedisCacheCircuitBreaker circuitBreaker = new RedisCacheCircuitBreaker();
    private final DropCacheErrorHandler errorHandler = new DropCacheErrorHandler(
            readFailureContext,
            circuitBreaker
    );

    @Test
    void ignoresReadAndWriteFailureSoReadRequestCanContinue() {
        Cache cache = cache("dropList");
        RuntimeException failure = new IllegalStateException("Redis unavailable");

        assertDoesNotThrow(() ->
                errorHandler.handleCacheGetError(failure, cache, "latest:first:20"));
        assertDoesNotThrow(() ->
                errorHandler.handleCachePutError(failure, cache, "latest:first:20", "value"));
        org.junit.jupiter.api.Assertions.assertTrue(readFailureContext.consumeFailure());
        org.junit.jupiter.api.Assertions.assertTrue(circuitBreaker.isOpen());
    }

    @Test
    void propagatesEvictionFailureToProtectCacheConsistency() {
        Cache cache = cache("dropList");
        RuntimeException failure = new IllegalStateException("Redis unavailable");

        assertThrows(RuntimeException.class, () ->
                errorHandler.handleCacheEvictError(failure, cache, "latest:first:20"));
        assertThrows(RuntimeException.class, () ->
                errorHandler.handleCacheClearError(failure, cache));
    }

    private Cache cache(String name) {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn(name);
        return cache;
    }
}
