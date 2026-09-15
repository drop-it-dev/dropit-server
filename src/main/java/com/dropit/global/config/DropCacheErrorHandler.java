package com.dropit.global.config;

import com.dropit.drop.cache.CacheReadFailureContext;
import com.dropit.drop.cache.RedisCacheCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DropCacheErrorHandler extends SimpleCacheErrorHandler {

    private final CacheReadFailureContext readFailureContext;
    private final RedisCacheCircuitBreaker circuitBreaker;
    private final AtomicBoolean getWarningLogged = new AtomicBoolean();
    private final AtomicBoolean putWarningLogged = new AtomicBoolean();

    public DropCacheErrorHandler(
            CacheReadFailureContext readFailureContext,
            RedisCacheCircuitBreaker circuitBreaker
    ) {
        this.readFailureContext = readFailureContext;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        if (RedisCacheConfig.DROP_LIST_CACHE.equals(cache.getName())) {
            readFailureContext.markFailed();
        }
        circuitBreaker.open();
        if (getWarningLogged.compareAndSet(false, true)) {
            log.warn("캐시 조회에 실패해 원본 저장소를 사용합니다. cache={}, key={}, cause={}",
                    cache.getName(), key, exception.toString());
        }
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        circuitBreaker.open();
        if (putWarningLogged.compareAndSet(false, true)) {
            log.warn("캐시 저장에 실패했지만 조회 결과는 반환합니다. cache={}, key={}, cause={}",
                    cache.getName(), key, exception.toString());
        }
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        circuitBreaker.open();
        super.handleCacheEvictError(exception, cache, key);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        circuitBreaker.open();
        super.handleCacheClearError(exception, cache);
    }
}
