package com.dropit.drop.service;

import com.dropit.drop.cache.DropDetailCacheValue;
import com.dropit.drop.cache.DropSaleCacheWriter;
import com.dropit.drop.dto.request.DropVisibilityUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.config.RedisCacheConfig;
import com.dropit.order.redis.RedisOrderKeyFactory;
import com.dropit.global.exception.ServiceException;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
@Import({DropService.class, DropVisibilityService.class, RedisCacheConfig.class, DropCacheIntegrationTest.RedisTestConfig.class})
class DropCacheIntegrationTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Autowired
    private DropService dropService;

    @Autowired
    private DropVisibilityService dropVisibilityService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private DropRepository dropRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private OrderItemRepository orderItemRepository;

    @MockitoBean
    private DropUserPurchaseRepository dropUserPurchaseRepository;

    @MockitoBean
    private DropSaleCacheWriter dropSaleCacheWriter;

    @MockitoBean
    private TransactionTemplate transactionTemplate;

    private Cache dropDetailCache;

    @BeforeEach
    void clearCache() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        dropDetailCache = cacheManager.getCache(RedisCacheConfig.DROP_DETAIL_CACHE);
        assertNotNull(dropDetailCache);
        dropDetailCache.clear();
        redisTemplate.delete(RedisOrderKeyFactory.stockKey(100L));
    }

    @Test
    void repeatedDetailRequestUsesRedisCache() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));
        redisTemplate.opsForValue().set(RedisOrderKeyFactory.stockKey(100L), "100");

        DropResponse first = dropService.getOne(100L);
        DropResponse second = dropService.getOne(100L);

        assertEquals(first, second);
        verify(dropRepository, times(1)).findDetailById(100L);
        assertNotNull(dropDetailCache.get(100L, DropDetailCacheValue.class));
    }

    @Test
    void changedRedisStockIsReflectedWithoutReloadingMetadata() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));
        String stockKey = RedisOrderKeyFactory.stockKey(100L);
        redisTemplate.opsForValue().set(stockKey, "100");

        DropResponse first = dropService.getOne(100L);
        redisTemplate.opsForValue().set(stockKey, "97");
        DropResponse second = dropService.getOne(100L);

        assertEquals(100, first.remainingQuantity());
        assertEquals(97, second.remainingQuantity());
        assertEquals(3, second.soldQuantity());
        verify(dropRepository, times(1)).findDetailById(100L);
    }

    @Test
    void changingVisibilityEvictsCachedDetail() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));
        when(dropRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(drop));

        redisTemplate.opsForValue().set(RedisOrderKeyFactory.stockKey(100L), "100");
        dropService.getOne(100L);

        dropVisibilityService.changeVisibility(1L, 100L, new DropVisibilityUpdateRequest(false));

        assertNull(dropDetailCache.get(100L));
    }

    @Test
    void cacheSyncFailureAfterVisibilityChangeStillEvictsCachedDetail() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));
        when(dropRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(drop));
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(dropSaleCacheWriter).apply(any());
      
        redisTemplate.opsForValue().set(RedisOrderKeyFactory.stockKey(100L), "100");
        dropService.getOne(100L);

        assertThrows(ServiceException.class, () ->
                dropVisibilityService.changeVisibility(1L, 100L, new DropVisibilityUpdateRequest(false)));

        assertNull(dropDetailCache.get(100L));
    }

    private Drop visibleDrop() {
        User seller = new User("seller@cache.test", "password", "cache-seller", UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        Product product = new Product(seller, "Limited Hoodie", "Cache integration fixture", null);
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
    static class RedisTestConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        }
    }
}
