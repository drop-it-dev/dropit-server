package com.dropit.global.config;

import org.springframework.cache.annotation.EnableCaching;
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
public class RedisCacheConfig {

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
        RedisCacheConfiguration dropDetailConfiguration = defaultConfiguration.entryTtl(DROP_DETAIL_TTL);
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory)
                .withStatisticsCollector(CacheStatisticsCollector.create());

        return new FailOpenRedisCacheManager(
                cacheWriter,
                defaultConfiguration,
                Map.of(DROP_DETAIL_CACHE, dropDetailConfiguration),
                errorHandler()
        );
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler();
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

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withCacheConfiguration(
                        DROP_DETAIL_CACHE,
                        defaultConfiguration.entryTtl(DROP_DETAIL_TTL)
                )
                .disableCreateOnMissingCache()
                .transactionAware()
                .enableStatistics()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new DropCacheErrorHandler(
                cacheReadFailureContext,
                redisCacheCircuitBreaker
        );
    }
}
