package com.dropit.drop.cache;

import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.config.FailOpenCacheErrorHandler;
import com.dropit.global.config.RedisCacheConfig;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig
@Import({DropDetailCacheReader.class, DropDetailCacheFallbackTest.FailingCacheConfig.class})
class DropDetailCacheFallbackTest {

    @MockitoBean
    private DropRepository dropRepository;

    @Autowired
    private DropDetailCacheReader cacheReader;

    @Test
    void cacheFailureFallsBackToDatabase() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));

        DropDetailCacheValue result = cacheReader.get(100L);

        assertEquals(100L, result.id());
        verify(dropRepository).findDetailById(100L);
    }

    private Drop visibleDrop() {
        User seller = new User("seller@cache.test", "password", "cache-seller", UserRole.SELLER);
        ReflectionTestUtils.setField(seller, "id", 1L);
        Product product = new Product(seller, "Limited Hoodie", "description", null);
        ReflectionTestUtils.setField(product, "id", 10L);
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        Drop drop = new Drop(product, new BigDecimal("59000"), 100, 10, 2, openAt, openAt.plusDays(1));
        drop.changeVisibility(true);
        ReflectionTestUtils.setField(drop, "id", 100L);
        return drop;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableCaching
    static class FailingCacheConfig implements CachingConfigurer {

        @Bean
        @Override
        public CacheManager cacheManager() {
            Cache cache = new FailingCache(RedisCacheConfig.DROP_DETAIL_CACHE);
            return new CacheManager() {
                @Override
                public Cache getCache(String name) {
                    return RedisCacheConfig.DROP_DETAIL_CACHE.equals(name) ? cache : null;
                }

                @Override
                public Collection<String> getCacheNames() {
                    return List.of(RedisCacheConfig.DROP_DETAIL_CACHE);
                }
            };
        }

        @Override
        public CacheErrorHandler errorHandler() {
            return new FailOpenCacheErrorHandler();
        }
    }

    static class FailingCache extends ConcurrentMapCache {

        FailingCache(String name) {
            super(name);
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            throw new IllegalStateException("Redis unavailable");
        }

        @Override
        public void put(Object key, Object value) {
            throw new IllegalStateException("Redis unavailable");
        }
    }
}
