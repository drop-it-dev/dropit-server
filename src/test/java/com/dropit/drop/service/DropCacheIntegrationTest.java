package com.dropit.drop.service;

import com.dropit.drop.cache.DropDetailCacheValue;
import com.dropit.drop.cache.DropDetailCacheReader;
import com.dropit.drop.cache.CacheReadFailureContext;
import com.dropit.drop.cache.DropListCacheLoader;
import com.dropit.drop.cache.DropListCacheMetrics;
import com.dropit.drop.cache.DropListCacheReader;
import com.dropit.drop.cache.DropListCacheValue;
import com.dropit.drop.cache.DropListLocalReadCache;
import com.dropit.drop.cache.DropListLocalFallback;
import com.dropit.drop.cache.DropListStockDbFallbackCache;
import com.dropit.drop.cache.DropListStockReader;
import com.dropit.drop.cache.RedisCacheCircuitBreaker;
import com.dropit.drop.cache.RedisStockCircuitBreaker;
import com.dropit.drop.cache.DropSaleCacheWriter;
import com.dropit.drop.dto.request.DropSearchCondition;
import com.dropit.drop.dto.request.DropUpdateRequest;
import com.dropit.drop.dto.request.DropVisibilityUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropLiveState;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.config.RedisCacheConfig;
import com.dropit.global.exception.ServiceException;
import com.dropit.global.storage.S3ImageService;
import com.dropit.order.repository.DropUserPurchaseRepository;
import com.dropit.order.repository.OrderItemRepository;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.product.dto.request.ProductUpdateRequest;
import com.dropit.product.service.ProductService;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import java.util.List;
import java.util.Map;

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
@Import({
        DropService.class,
        DropVisibilityService.class,
        DropDetailCacheReader.class,
        DropListCacheReader.class,
        DropListCacheLoader.class,
        DropListStockReader.class,
        DropListStockDbFallbackCache.class,
        DropListLocalFallback.class,
        DropListLocalReadCache.class,
        CacheReadFailureContext.class,
        RedisCacheCircuitBreaker.class,
        RedisStockCircuitBreaker.class,
        ProductService.class,
        RedisCacheConfig.class,
        DropCacheIntegrationTest.RedisTestConfig.class
})
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
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private DropListLocalReadCache dropListLocalReadCache;

    @MockitoBean
    private DropRepository dropRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private S3ImageService s3ImageService;

    @MockitoBean
    private OrderItemRepository orderItemRepository;

    @MockitoBean
    private DropUserPurchaseRepository dropUserPurchaseRepository;

    @MockitoBean
    private DropSaleCacheWriter dropSaleCacheWriter;

    @MockitoBean
    private DropListCacheMetrics dropListCacheMetrics;

    @MockitoBean
    private TransactionTemplate transactionTemplate;

    private Cache dropDetailCache;
    private Cache dropListCache;

    @BeforeEach
    void clearCache() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        dropDetailCache = cacheManager.getCache(RedisCacheConfig.DROP_DETAIL_CACHE);
        assertNotNull(dropDetailCache);
        dropDetailCache.clear();
        dropListCache = cacheManager.getCache(RedisCacheConfig.DROP_LIST_CACHE);
        assertNotNull(dropListCache);
        dropListCache.clear();
        dropListLocalReadCache.invalidate();
    }

    @Test
    void repeatedDetailRequestUsesRedisCache() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));
        when(dropRepository.findLiveStateById(100L)).thenReturn(Optional.of(liveState(100, true)));

        DropResponse first = dropService.getOne(100L);
        DropResponse second = dropService.getOne(100L);

        assertEquals(first, second);
        verify(dropRepository, times(1)).findDetailById(100L);
        verify(dropRepository, times(2)).findLiveStateById(100L);
        assertNotNull(dropDetailCache.get(100L, DropDetailCacheValue.class));
    }

    @Test
    void changedDatabaseStockIsReflectedWithoutReloadingMetadata() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));
        when(dropRepository.findLiveStateById(100L)).thenReturn(
                Optional.of(liveState(100, true)),
                Optional.of(liveState(97, true))
        );

        DropResponse first = dropService.getOne(100L);
        DropResponse second = dropService.getOne(100L);

        assertEquals(100, first.remainingQuantity());
        assertEquals(97, second.remainingQuantity());
        assertEquals(3, second.soldQuantity());
        verify(dropRepository, times(1)).findDetailById(100L);
        verify(dropRepository, times(2)).findLiveStateById(100L);
    }

    @Test
    void changingVisibilityEvictsCachedDetail() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));
        when(dropRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(drop));
        when(dropRepository.findLiveStateById(100L)).thenReturn(Optional.of(liveState(100, true)));
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
        when(dropRepository.findLiveStateById(100L)).thenReturn(Optional.of(liveState(100, true)));
        dropService.getOne(100L);

        assertThrows(ServiceException.class, () ->
                dropVisibilityService.changeVisibility(1L, 100L, new DropVisibilityUpdateRequest(false)));

        assertNull(dropDetailCache.get(100L));
    }

    @Test
    void repeatedLatestFirstPageUsesRedisListCache() {
        Drop drop = visibleDrop();
        DropSearchCondition condition = new DropSearchCondition(null, null, null);
        PageRequest pageable = PageRequest.of(0, 20);
        when(dropRepository.searchPublicDrops(any(), any()))
                .thenReturn(new PageImpl<>(List.of(drop), pageable, 1));
        when(dropRepository.findRemainingQuantitiesByIds(List.of(100L)))
                .thenReturn(Map.of(100L, 73));

        DropResponse first = dropService.getAll(condition, pageable).getContent().getFirst();
        DropResponse second = dropService.getAll(condition, pageable).getContent().getFirst();

        assertEquals(73, first.remainingQuantity());
        assertEquals(first, second);
        verify(dropRepository, times(1)).searchPublicDrops(any(), any());
        assertNotNull(dropListCache.get("latest:first:20", DropListCacheValue.class));
    }

    @Test
    void changingVisibilityEvictsCachedList() {
        Drop drop = visibleDrop();
        DropSearchCondition condition = new DropSearchCondition(null, null, null);
        PageRequest pageable = PageRequest.of(0, 20);
        when(dropRepository.searchPublicDrops(any(), any()))
                .thenReturn(new PageImpl<>(List.of(drop), pageable, 1));
        when(dropRepository.findRemainingQuantitiesByIds(List.of(100L)))
                .thenReturn(Map.of(100L, 100));
        when(dropRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(drop));
        dropService.getAll(condition, pageable);

        dropVisibilityService.changeVisibility(1L, 100L, new DropVisibilityUpdateRequest(false));

        assertNull(dropListCache.get("latest:first:20"));
    }

    @Test
    void updatingDropEvictsCachedList() {
        Drop drop = visibleDrop();
        cacheLatestFirstPage(drop);
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));
        LocalDateTime openAt = LocalDateTime.now().plusDays(2);

        dropService.update(1L, 100L, new DropUpdateRequest(
                new BigDecimal("65000"),
                120,
                5,
                2,
                openAt,
                openAt.plusDays(1)
        ));

        assertNull(dropListCache.get("latest:first:20"));
    }

    @Test
    void updatingProductEvictsCachedDropList() {
        Drop drop = visibleDrop();
        cacheLatestFirstPage(drop);
        Product product = drop.getProduct();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        productService.update(
                1L,
                10L,
                new ProductUpdateRequest("Changed Product", "Changed description")
        );

        assertNull(dropListCache.get("latest:first:20"));
    }

    private void cacheLatestFirstPage(Drop drop) {
        DropSearchCondition condition = new DropSearchCondition(null, null, null);
        PageRequest pageable = PageRequest.of(0, 20);
        when(dropRepository.searchPublicDrops(any(), any()))
                .thenReturn(new PageImpl<>(List.of(drop), pageable, 1));
        when(dropRepository.findRemainingQuantitiesByIds(List.of(100L)))
                .thenReturn(Map.of(100L, 100));
        dropService.getAll(condition, pageable);
        assertNotNull(dropListCache.get("latest:first:20"));
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

    private DropLiveState liveState(int remainingQuantity, boolean visible) {
        return new DropLiveState() {
            @Override
            public int getRemainingQuantity() {
                return remainingQuantity;
            }

            @Override
            public boolean isVisible() {
                return visible;
            }
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisTestConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }
    }
}
