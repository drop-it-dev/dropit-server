package com.dropit.global.config;

import com.dropit.drop.cache.CacheReadFailureContext;
import com.dropit.drop.cache.RedisCacheCircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

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

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration)
                .withCacheConfiguration(
                        DROP_DETAIL_CACHE,
                        defaultConfiguration.entryTtl(DROP_DETAIL_TTL)
                )
                .withCacheConfiguration(
                        DROP_LIST_CACHE,
                        defaultConfiguration.entryTtl(DROP_LIST_TTL)
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
