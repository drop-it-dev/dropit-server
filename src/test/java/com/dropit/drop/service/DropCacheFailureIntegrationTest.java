package com.dropit.drop.service;

import com.dropit.drop.cache.DropListCacheLoader;
import com.dropit.drop.cache.DropDetailCacheReader;
import com.dropit.drop.cache.CacheReadFailureContext;
import com.dropit.drop.cache.DropListCacheMetrics;
import com.dropit.drop.cache.DropListCacheReader;
import com.dropit.drop.cache.DropListStockReader;
import com.dropit.drop.cache.DropListStockDbFallbackCache;
import com.dropit.drop.cache.DropListLocalFallback;
import com.dropit.drop.cache.DropListLocalReadCache;
import com.dropit.drop.cache.RedisCacheCircuitBreaker;
import com.dropit.drop.cache.RedisStockCircuitBreaker;
import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.config.RedisCacheConfig;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
@Import({
        DropService.class,
        DropListCacheReader.class,
        DropListCacheLoader.class,
        DropListStockReader.class,
        DropListStockDbFallbackCache.class,
        DropListLocalFallback.class,
        DropListLocalReadCache.class,
        CacheReadFailureContext.class,
        RedisCacheCircuitBreaker.class,
        RedisStockCircuitBreaker.class,
        RedisCacheConfig.class,
        DropCacheFailureIntegrationTest.UnavailableRedisConfig.class
})
class DropCacheFailureIntegrationTest {

    @MockitoBean
    private DropDetailCacheReader dropDetailCacheReader;

    @MockitoBean
    private DropRepository dropRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private DropListCacheMetrics cacheMetrics;

    @jakarta.annotation.Resource
    private DropService dropService;

    @Test
    void returnsDatabaseResultWhenRedisIsUnavailable() {
        Drop drop = visibleDrop();
        PageRequest pageable = PageRequest.of(0, 20);
        when(dropRepository.searchPublicDrops(any(), any()))
                .thenReturn(new PageImpl<>(List.of(drop), pageable, 1));
        when(dropRepository.findRemainingQuantitiesByIds(List.of(100L)))
                .thenReturn(Map.of(100L, 67));

        Page<DropResponse> first = dropService.getAll(
                new DropSearchCondition(null, null, null),
                pageable
        );
        Page<DropResponse> second = dropService.getAll(
                new DropSearchCondition(null, null, null),
                pageable
        );

        assertEquals(1, first.getTotalElements());
        assertEquals(67, first.getContent().getFirst().remainingQuantity());
        assertEquals(first, second);
        verify(dropRepository, times(1)).searchPublicDrops(any(), any());
        verify(dropRepository, times(1)).findRemainingQuantitiesByIds(List.of(100L));
    }

    private Drop visibleDrop() {
        User seller = new User("failure@test.local", "password", "failure-seller", UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        Product product = new Product(seller, "Failure Test Product", "description", null);
        ReflectionTestUtils.setField(product, "id", 10L);
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        Drop drop = new Drop(
                product,
                new BigDecimal("59000"),
                100,
                10,
                2,
                openAt,
                openAt.plusDays(1)
        );
        drop.changeVisibility(true);
        ReflectionTestUtils.setField(drop, "id", 100L);
        return drop;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UnavailableRedisConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            RedisStandaloneConfiguration server = new RedisStandaloneConfiguration("127.0.0.1", 1);
            LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(200))
                    .shutdownTimeout(Duration.ZERO)
                    .build();
            return new LettuceConnectionFactory(server, client);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }
    }
}
