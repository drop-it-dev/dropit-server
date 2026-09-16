package com.dropit.ranking.batch;

import com.dropit.ranking.repository.SellerRankingSnapshotStore;
import com.dropit.ranking.repository.SellerSales;
import com.dropit.ranking.repository.SellerSalesRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SellerRankingBatchIntegrationTest {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    private static JdbcTemplate jdbc;
    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private static final Instant AS_OF = Instant.parse("2025-09-16T03:00:00Z");
    @TempDir Path tempDirectory;

    @BeforeAll
    static void start() {
        MYSQL.start();
        REDIS.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.execute("CREATE TABLE products (id BIGINT PRIMARY KEY, seller_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE drops (id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL, visible BOOLEAN NOT NULL)");
        jdbc.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL, created_at DATETIME(6))");
        jdbc.execute("CREATE TABLE order_items (id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, drop_id BIGINT NOT NULL, quantity INT NOT NULL)");
        connections = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connections.afterPropertiesSet();
        redis = new StringRedisTemplate(connections);
    }

    @AfterAll
    static void stop() {
        if (connections != null) connections.destroy();
        REDIS.stop();
        MYSQL.stop();
    }

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM order_items");
        jdbc.execute("DELETE FROM orders");
        jdbc.execute("DELETE FROM drops");
        jdbc.execute("DELETE FROM products");
        jdbc.execute("INSERT INTO products VALUES (1, 10), (2, 10), (3, 20), (4, 30)");
        jdbc.execute("INSERT INTO drops VALUES (1, 1, true), (2, 2, false), (3, 3, true), (4, 4, true)");
        jdbc.execute("""
                INSERT INTO orders VALUES
                  (1, 'ORDERED', '2025-08-31 14:59:59.999999'),
                  (2, 'ORDERED', '2025-08-31 15:00:00'),
                  (3, 'CANCELED', '2025-09-01 00:00:00'),
                  (4, 'ORDERED', '2025-09-16 02:59:59.999999'),
                  (5, 'ORDERED', '2025-09-16 03:00:00'),
                  (6, 'ORDERED', '2025-09-16 03:00:00.000001')
                """);
        jdbc.execute("""
                INSERT INTO order_items VALUES
                  (1, 1, 1, 4), (2, 2, 1, 3), (3, 2, 2, 2),
                  (4, 3, 3, 100), (5, 4, 3, 6), (6, 5, 4, 7), (7, 6, 4, 8)
                """);
        try (var connection = connections.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void aggregatesQuantityAcrossProductsAndExcludesCancellationsAndCutoffBoundary() {
        var repository = new SellerSalesRepository(new NamedParameterJdbcTemplate(jdbc));
        var result = repository.aggregate(LocalDateTime.parse("2025-08-31T15:00:00"),
                LocalDateTime.parse("2025-09-16T03:00:00"));
        assertEquals(List.of(new SellerSales(10, 9, 5), new SellerSales(20, 6, 6)), result);

        jdbc.update("UPDATE orders SET status = 'CANCELED' WHERE id = 2");
        assertEquals(List.of(new SellerSales(10, 4, 0), new SellerSales(20, 6, 6)),
                repository.aggregate(LocalDateTime.parse("2025-08-31T15:00:00"),
                        LocalDateTime.parse("2025-09-16T03:00:00")));
    }

    @Test
    void batchContextLoadsOnlyJdbcAndRedisAndNeverRecreatesSchema() {
        var args = arguments("--spring.jpa.hibernate.ddl-auto=create");
        try (var context = new SpringApplicationBuilder(SellerRankingBatch.class)
                .web(WebApplicationType.NONE).profiles("ranking").run(args.toArray(String[]::new))) {
            assertNotNull(context.getBean(SellerRankingJob.class));
            assertFalse(context.containsBean("entityManagerFactory"));
            assertFalse(context.containsBean("sqsAsyncClient"));
            assertFalse(context.containsBean("s3Client"));
            assertFalse(context.containsBean("jwtFilter"));
            assertFalse(context.containsBean("orderController"));
            assertFalse(context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
            context.getBean(SellerRankingJob.class).run(AS_OF);
        }
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
        assertEquals(9d, redis.opsForZSet().score(SellerRankingSnapshotStore.TOTAL_KEY, "10"));
        assertEquals(5d, redis.opsForZSet().score(
                SellerRankingSnapshotStore.monthlyKey(YearMonth.parse("2025-09")), "10"));
    }

    @Test
    void realApplicationProcessPublishesAndExitsWithoutWebOrConsumerSettings() throws Exception {
        String output = runProcess(0);
        assertTrue(output.contains("Seller ranking completed"), output);
        assertEquals(Set.of("10", "20"), redis.opsForZSet().range(SellerRankingSnapshotStore.TOTAL_KEY, 0, -1));
        assertEquals(AS_OF.toString(), redis.opsForHash().get(SellerRankingSnapshotStore.METADATA_KEY, "asOf"));
    }

    @Test
    void failedDatabaseQueryExitsNonzeroAndPreservesPublishedSnapshot() throws Exception {
        var store = new SellerRankingSnapshotStore(redis);
        var execution = new SellerRankingExecution(UUID.randomUUID(), YearMonth.parse("2025-09"), AS_OF, AS_OF);
        store.publish(execution, AS_OF, List.of(new SellerSales(99, 7, 4)));
        runProcess(1, "--spring.datasource.password=incorrect-password");
        assertEquals(Set.of("99"), redis.opsForZSet().range(SellerRankingSnapshotStore.TOTAL_KEY, 0, -1));
        assertEquals(AS_OF.toString(), redis.opsForHash().get(SellerRankingSnapshotStore.METADATA_KEY, "publishedAt"));
    }

    @Test
    void missingOrderTimezoneFailsRatherThanGuessingMonthBoundaries() throws Exception {
        runProcess(1, "--app.ranking.order-time-zone=");
        assertFalse(redis.hasKey(SellerRankingSnapshotStore.METADATA_KEY));
    }

    private String runProcess(int expectedExitCode, String... overrides) throws Exception {
        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("test.runtime-classpath"),
                "com.dropit.DropitServerApplication"));
        command.addAll(arguments(overrides));
        Path output = tempDirectory.resolve("batch-" + System.nanoTime() + ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().remove("SPRING_PROFILES_ACTIVE");
        builder.environment().remove("SPRING_CONFIG_IMPORT");
        var process = builder.start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Batch did not exit within 45 seconds");
            String log = Files.readString(output);
            assertEquals(expectedExitCode, process.exitValue(), log);
            return log;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static List<String> arguments(String... overrides) {
        var values = new java.util.LinkedHashMap<>(Map.of(
                "job", "seller-ranking",
                "spring.config.location", "classpath:/application-ranking.properties",
                "spring.datasource.url", MYSQL.getJdbcUrl(),
                "spring.datasource.username", MYSQL.getUsername(),
                "spring.datasource.password", MYSQL.getPassword(),
                "spring.data.redis.host", REDIS.getHost(),
                "spring.data.redis.port", REDIS.getMappedPort(6379).toString(),
                "app.ranking.order-time-zone", "UTC",
                "app.ranking.as-of", AS_OF.toString()));
        Arrays.stream(overrides).forEach(arg -> {
            String[] parts = arg.substring(2).split("=", 2);
            values.put(parts[0], parts[1]);
        });
        return values.entrySet().stream().map(entry -> "--" + entry.getKey() + "=" + entry.getValue()).toList();
    }
}
