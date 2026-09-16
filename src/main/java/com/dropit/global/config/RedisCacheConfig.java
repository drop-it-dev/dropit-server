package com.dropit.global.config;

import com.dropit.drop.cache.CacheReadFailureContext;
import com.dropit.drop.cache.RedisCacheCircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisCacheConfig implements CachingConfigurer {

    private final CacheReadFailureContext cacheReadFailureContext;
    private final RedisCacheCircuitBreaker redisCacheCircuitBreaker;

    public static final String DROP_DETAIL_CACHE = "dropDetail";
    public static final String DROP_LIST_CACHE = "dropList";
    public static final Duration DROP_DETAIL_TTL = Duration.ofSeconds(3);
    public static final Duration DROP_LIST_TTL = Duration.ofMinutes(5);

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "dropit::" + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        RedisSerializer.string()
                ))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        RedisSerializer.json()
                ));
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory)
                .withStatisticsCollector(CacheStatisticsCollector.create());

        return new FailOpenRedisCacheManager(
                cacheWriter,
                defaultConfiguration,
                Map.of(
                        DROP_DETAIL_CACHE, defaultConfiguration.entryTtl(DROP_DETAIL_TTL),
                        DROP_LIST_CACHE, defaultConfiguration.entryTtl(DROP_LIST_TTL)
                ),
                errorHandler()
        );
    }

    @Override
    public CacheErrorHandler errorHandler() {
        CacheErrorHandler detailHandler = new FailOpenCacheErrorHandler();
        CacheErrorHandler listHandler = new DropCacheErrorHandler(
                cacheReadFailureContext,
                redisCacheCircuitBreaker
        );
        return new CacheErrorHandler() {
            private CacheErrorHandler forCache(Cache cache) {
                return DROP_LIST_CACHE.equals(cache.getName()) ? listHandler : detailHandler;
            }

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                forCache(cache).handleCacheGetError(exception, cache, key);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                forCache(cache).handleCachePutError(exception, cache, key, value);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                forCache(cache).handleCacheEvictError(exception, cache, key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                forCache(cache).handleCacheClearError(exception, cache);
            }
        };
    }

    private static final class FailOpenRedisCacheManager extends RedisCacheManager {

        private final CacheErrorHandler errorHandler;

        private FailOpenRedisCacheManager(
                RedisCacheWriter cacheWriter,
                RedisCacheConfiguration defaultConfiguration,
                Map<String, RedisCacheConfiguration> cacheConfigurations,
                CacheErrorHandler errorHandler
        ) {
            super(cacheWriter, defaultConfiguration, false, cacheConfigurations);
            this.errorHandler = errorHandler;
        }

        @Override
        protected Cache decorateCache(Cache cache) {
            return new FailOpenTransactionAwareCacheDecorator(cache, errorHandler);
        }
    }

    private static final class FailOpenTransactionAwareCacheDecorator extends TransactionAwareCacheDecorator {

        private final CacheErrorHandler errorHandler;

        private FailOpenTransactionAwareCacheDecorator(Cache targetCache, CacheErrorHandler errorHandler) {
            super(targetCache);
            this.errorHandler = errorHandler;
        }

        @Override
        public void evict(Object key) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictNow(key);
                    }
                });
                return;
            }
            evictNow(key);
        }

        private void evictNow(Object key) {
            try {
                getTargetCache().evict(key);
            } catch (RuntimeException exception) {
                errorHandler.handleCacheEvictError(exception, getTargetCache(), key);
            }
        }
    }
}
