package com.dropit.drop.service;

import com.dropit.drop.dto.request.DropVisibilityUpdateRequest;
import com.dropit.drop.dto.response.DropResponse;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.config.RedisCacheConfig;
import com.dropit.global.storage.S3ImageService;
import com.dropit.product.entity.Product;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class DropCacheIntegrationTest {

    @Autowired
    private DropService dropService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private DropRepository dropRepository;

    @MockitoBean
    private S3ImageService s3ImageService;

    private Cache dropDetailCache;

    @BeforeEach
    void clearCache() {
        dropDetailCache = cacheManager.getCache(RedisCacheConfig.DROP_DETAIL_CACHE);
        assertNotNull(dropDetailCache);
        dropDetailCache.clear();
    }

    @Test
    void repeatedDetailRequestUsesRedisCache() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));

        DropResponse first = dropService.getOne(100L);
        DropResponse second = dropService.getOne(100L);

        assertEquals(first, second);
        verify(dropRepository, times(1)).findDetailById(100L);
        assertNotNull(dropDetailCache.get(100L, DropResponse.class));
    }

    @Test
    void changingVisibilityEvictsCachedDetail() {
        Drop drop = visibleDrop();
        when(dropRepository.findDetailById(100L)).thenReturn(Optional.of(drop));
        when(dropRepository.findById(100L)).thenReturn(Optional.of(drop));
        dropService.getOne(100L);

        dropService.changeVisibility(1L, 100L, new DropVisibilityUpdateRequest(false));

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
}
