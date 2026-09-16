package com.dropit.drop.cache;

import com.dropit.global.config.RedisCacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DropListCacheReader {

    private final DropListCacheLoader cacheLoader;
    private final DropListCacheMetrics cacheMetrics;
    private final CacheReadFailureContext readFailureContext;
    private final DropListLocalFallback localFallback;
    private final RedisCacheCircuitBreaker circuitBreaker;

    @Cacheable(
            cacheNames = RedisCacheConfig.DROP_LIST_CACHE,
            key = "'latest:first:20'",
            condition = "@redisCacheCircuitBreaker.shouldAttemptRedis()",
            sync = true
    )
    public DropListCacheValue getLatestFirstPage() {
        if (readFailureContext.consumeFailure() || circuitBreaker.isOpen()) {
            return localFallback.findFresh()
                    .map(value -> {
                        cacheMetrics.recordLocalFallback();
                        return value;
                    })
                    .orElseGet(this::loadFromDatabase);
        }

        return loadFromDatabase();
    }

    private DropListCacheValue loadFromDatabase() {
        cacheMetrics.recordLoad();
        return cacheLoader.loadLatestFirstPage();
    }
}
