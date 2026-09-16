package com.dropit.drop.service;

import com.dropit.drop.cache.DropDetailCacheReader;
import com.dropit.drop.cache.CacheReadFailureContext;
import com.dropit.drop.cache.DropListCacheLoader;
import com.dropit.drop.cache.DropListCacheMetrics;
import com.dropit.drop.cache.DropListCacheReader;
import com.dropit.drop.cache.DropListLocalFallback;
import com.dropit.drop.cache.DropListLocalReadCache;
import com.dropit.drop.cache.DropListStockReader;
import com.dropit.drop.cache.RedisCacheCircuitBreaker;
import com.dropit.drop.dto.request.DropUpdateRequest;
import com.dropit.drop.entity.Drop;
import com.dropit.drop.repository.DropRepository;
import com.dropit.global.config.QuerydslConfig;
import com.dropit.global.config.RedisCacheConfig;
import com.dropit.product.entity.Product;
import com.dropit.product.repository.ProductRepository;
import com.dropit.user.entity.User;
import com.dropit.user.entity.UserRole;
import com.dropit.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DropService.class, DropDetailCacheReader.class, CacheReadFailureContext.class,
        RedisCacheCircuitBreaker.class, QuerydslConfig.class, RedisCacheConfig.class,
        DropCacheEvictionFailureIntegrationTest.RedisTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DropCacheEvictionFailureIntegrationTest {

    @MockitoBean
    private DropListCacheReader dropListCacheReader;

    @MockitoBean
    private DropListCacheLoader dropListCacheLoader;

    @MockitoBean
    private DropListStockReader dropListStockReader;

    @MockitoBean
    private DropListCacheMetrics dropListCacheMetrics;

    @MockitoBean
    private DropListLocalFallback dropListLocalFallback;

    @MockitoBean
    private DropListLocalReadCache dropListLocalReadCache;

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @Autowired
    private DropService dropService;

    @Autowired
    private DropRepository dropRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private LettuceConnectionFactory redisConnectionFactory;

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void redisListEvictionFailurePreventsUpdateAndDelete() {
        User seller = userRepository.save(new User(
                "cache-eviction@test.invalid",
                "unused",
                "cache-eviction-seller",
                UserRole.SELLER
        ));
        Product product = productRepository.save(new Product(seller, "product", "description", null));
        LocalDateTime openAt = LocalDateTime.now().plusDays(1);
        Drop updatedDrop = dropRepository.save(new Drop(
                product, BigDecimal.valueOf(1000), 10, 0, 1, openAt, openAt.plusDays(1)
        ));
        Drop deletedDrop = dropRepository.save(new Drop(
                product, BigDecimal.valueOf(2000), 10, 0, 1, openAt, openAt.plusDays(1)
        ));

        Cache cache = cacheManager.getCache(RedisCacheConfig.DROP_DETAIL_CACHE);
        assertNotNull(cache);
        cache.put(updatedDrop.getId(), "cached");
        cache.put(deletedDrop.getId(), "cached");
        REDIS.stop();
        redisConnectionFactory.destroy();
        assertThrows(IllegalStateException.class, redisConnectionFactory::getConnection);

        assertThrows(IllegalStateException.class, () -> dropService.update(
                seller.getId(),
                updatedDrop.getId(),
                new DropUpdateRequest(BigDecimal.valueOf(1500), null, null, null, null, null)
        ));
        assertThrows(IllegalStateException.class,
                () -> dropService.delete(seller.getId(), deletedDrop.getId()));

        assertEquals(BigDecimal.valueOf(1000), dropRepository.findById(updatedDrop.getId()).orElseThrow().getPrice());
        assertTrue(dropRepository.existsById(deletedDrop.getId()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RedisTestConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(
                    REDIS.getHost(),
                    REDIS.getMappedPort(6379)
            );
            LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(200))
                    .shutdownTimeout(Duration.ZERO)
                    .build();
            return new LettuceConnectionFactory(redis, client);
        }
    }
}
