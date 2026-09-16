package com.dropit.ranking.repository;

import com.dropit.ranking.batch.SellerRankingExecution;
import com.dropit.ranking.dto.request.RankingRange;
import com.dropit.ranking.dto.response.SellerRankingEntryResponse;
import com.dropit.ranking.dto.response.SellerRankingResponse;
import com.dropit.ranking.exception.RankingErrorCode;
import com.dropit.ranking.repository.SellerRankingQueryStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.YearMonth;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SellerRankingSnapshotStoreIntegrationTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private static SellerRankingSnapshotStore store;
    private static final Instant AS_OF = Instant.parse("2026-09-16T03:00:00Z");
    private static final String MONTHLY = SellerRankingSnapshotStore.monthlyKey(YearMonth.parse("2026-09"));

    @BeforeAll
    static void start() {
        REDIS.start();
        connections = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connections.afterPropertiesSet();
        redis = new StringRedisTemplate(connections);
        store = new SellerRankingSnapshotStore(redis);
    }

    @AfterAll
    static void stop() {
        if (connections != null) connections.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void reset() {
        try (var connection = connections.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void publishesBothPeriodsAndMetadataAndRecomputesCancellationWithoutIncrementing() {
        var sales = List.of(new SellerSales(1, 7, 4), new SellerSales(2, 10, 0));
        assertTrue(publish(AS_OF, sales));
        assertEquals(List.of("2", "1"), redis.opsForZSet()
                .reverseRange(SellerRankingSnapshotStore.TOTAL_KEY, 0, -1).stream().toList());
        assertEquals(Set.of("1"), redis.opsForZSet().range(MONTHLY, 0, -1));
        assertEquals(AS_OF.toString(), redis.opsForHash().get(SellerRankingSnapshotStore.METADATA_KEY, "asOf"));
        assertEquals("2026-09", redis.opsForHash().get(SellerRankingSnapshotStore.METADATA_KEY, "month"));
        assertEquals(-1L, redis.getExpire(SellerRankingSnapshotStore.TOTAL_KEY));
        assertEquals(-1L, redis.getExpire(MONTHLY));

        assertTrue(publish(AS_OF.plusSeconds(1), sales));
        assertEquals(7d, redis.opsForZSet().score(SellerRankingSnapshotStore.TOTAL_KEY, "1"));
        assertTrue(publish(AS_OF.plusSeconds(2), List.of(new SellerSales(1, 3, 0))));
        assertEquals(Set.of("1"), redis.opsForZSet().range(SellerRankingSnapshotStore.TOTAL_KEY, 0, -1));
        assertEquals(3d, redis.opsForZSet().score(SellerRankingSnapshotStore.TOTAL_KEY, "1"));
        assertFalse(redis.hasKey(MONTHLY));
        assertEquals("0", redis.opsForHash().get(SellerRankingSnapshotStore.METADATA_KEY, "monthlySellerCount"));
    }

    @Test
    void emptySnapshotClearsOldResultsButStillRecordsSuccessfulPublication() {
        publish(AS_OF, List.of(new SellerSales(1, 7, 4)));
        assertTrue(publish(AS_OF.plusSeconds(1), List.of()));
        assertFalse(redis.hasKey(SellerRankingSnapshotStore.TOTAL_KEY));
        assertFalse(redis.hasKey(MONTHLY));
        assertEquals("0", redis.opsForHash().get(SellerRankingSnapshotStore.METADATA_KEY, "totalSellerCount"));
    }

    @Test
    void staleOrDuplicateExecutionCannotOverwriteNewerResult() {
        publish(AS_OF.plusSeconds(10), List.of(new SellerSales(1, 7, 4)));
        assertFalse(publish(AS_OF.plusSeconds(9), List.of(new SellerSales(2, 8, 5))));
        assertFalse(publish(AS_OF.plusSeconds(10), List.of()));
        assertFalse(store.publish(execution(AS_OF.minusSeconds(3600), AS_OF.plusSeconds(20)),
                AS_OF.plusSeconds(21), List.of()));
        assertEquals(Set.of("1"), redis.opsForZSet().range(SellerRankingSnapshotStore.TOTAL_KEY, 0, -1));
    }

    @Test
    void competingPublishersLeaveTheNewestCompleteSnapshot() {
        var tasks = IntStream.rangeClosed(1, 12).mapToObj(i -> CompletableFuture.runAsync(() ->
                publish(AS_OF.plusSeconds(i), List.of(new SellerSales(i, i * 2L, i)))))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).join();
        assertEquals(Set.of("12"), redis.opsForZSet().range(SellerRankingSnapshotStore.TOTAL_KEY, 0, -1));
        assertEquals(24d, redis.opsForZSet().score(SellerRankingSnapshotStore.TOTAL_KEY, "12"));
        assertEquals(12d, redis.opsForZSet().score(MONTHLY, "12"));
    }

    @Test
    void incompleteStagingFailsBeforeChangingAnyLiveResult() {
        publish(AS_OF, List.of(new SellerSales(1, 7, 4)));
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/ranking/publish-seller-ranking.lua"));
        script.setResultType(Long.class);
        redis.opsForZSet().add("ranking:{seller}:incomplete-total", "2", 99);
        assertThrows(RuntimeException.class, () -> redis.execute(script, List.of(
                        "ranking:{seller}:incomplete-total", "ranking:{seller}:missing-monthly",
                        SellerRankingSnapshotStore.TOTAL_KEY, MONTHLY, SellerRankingSnapshotStore.METADATA_KEY,
                        SellerRankingExecutionStore.executionKey(UUID.randomUUID().toString())),
                Long.toString(AS_OF.toEpochMilli()), Long.toString(AS_OF.plusSeconds(1).toEpochMilli()),
                AS_OF.toString(), AS_OF.plusSeconds(1).toString(), "2026-09",
                UUID.randomUUID().toString(), "1", "1"));
        assertEquals(7d, redis.opsForZSet().score(SellerRankingSnapshotStore.TOTAL_KEY, "1"));
        assertEquals(4d, redis.opsForZSet().score(MONTHLY, "1"));
        assertEquals(AS_OF.toString(), redis.opsForHash().get(SellerRankingSnapshotStore.METADATA_KEY, "publishedAt"));
    }

    @Test
    void duplicateSellersFailWithoutChangingOldSnapshot() {
        publish(AS_OF, List.of(new SellerSales(1, 7, 4)));
        assertThrows(IllegalArgumentException.class, () -> publish(AS_OF.plusSeconds(1),
                List.of(new SellerSales(2, 1, 1), new SellerSales(2, 2, 2))));
        assertEquals(Set.of("1"), redis.opsForZSet().range(SellerRankingSnapshotStore.TOTAL_KEY, 0, -1));
    }

    @Test
    void monthRolloverPublishesAnEmptyNewMonthAndUpdatesMetadata() {
        publish(AS_OF, List.of(new SellerSales(1, 7, 4)));
        Instant october = Instant.parse("2026-09-30T15:00:00Z");
        assertTrue(store.publish(execution(october, october), october, List.of(new SellerSales(1, 7, 0))));
        assertEquals("2026-10", redis.opsForHash().get(SellerRankingSnapshotStore.METADATA_KEY, "month"));
        assertFalse(redis.hasKey(SellerRankingSnapshotStore.monthlyKey(YearMonth.parse("2026-10"))));
        assertEquals(7d, redis.opsForZSet().score(SellerRankingSnapshotStore.TOTAL_KEY, "1"));
    }

    @Test
    void stagesMultipleChunksAndPreservesExactIntegerScores() {
        var sales = IntStream.rangeClosed(1, 1_001)
                .mapToObj(i -> new SellerSales(i, 9_007_199_254_740_991L, i)).toList();
        publish(AS_OF, sales);
        assertEquals(1_001L, redis.opsForZSet().zCard(SellerRankingSnapshotStore.TOTAL_KEY));
        assertEquals(1_001L, redis.opsForZSet().zCard(MONTHLY));
        assertEquals(9_007_199_254_740_991d, redis.opsForZSet().score(SellerRankingSnapshotStore.TOTAL_KEY, "1"));
    }

    @Test
    void lockCanOnlyBeReleasedByItsOwnerAndExecutionHistoryIsRecorded() {
        var executions = new SellerRankingExecutionStore(redis, Duration.ofMinutes(65));
        var first = execution(AS_OF, AS_OF);
        var second = execution(AS_OF, AS_OF.plusSeconds(1));

        assertTrue(executions.tryAcquire(first));
        assertFalse(executions.tryAcquire(second));
        executions.recordStarted(first);
        executions.release(second);
        assertFalse(executions.tryAcquire(second));
        executions.release(first);
        assertTrue(executions.tryAcquire(second));
        assertEquals("STARTED", redis.opsForHash().get(
                SellerRankingExecutionStore.executionKey(first.version().toString()), "status"));
    }

    @Test
    void readsTopRanksAndRequestedSellerAndDetectsMissingSnapshotData() {
        publish(AS_OF, List.of(
                new SellerSales(1, 7, 4),
                new SellerSales(2, 10, 4),
                new SellerSales(3, 7, 0)));
        var query = new SellerRankingQueryStore(redis);

        SellerRankingResponse response = query.read(
                RankingRange.TOTAL, YearMonth.parse("2026-09"), 2, 1L);

        assertEquals(List.of(2L, 3L), response.rankings().stream()
                .map(SellerRankingEntryResponse::sellerId).toList());
        assertEquals(3L, response.requestedSeller().rank());
        assertEquals(7L, response.requestedSeller().salesQuantity());

        redis.delete(SellerRankingSnapshotStore.TOTAL_KEY);
        var exception = assertThrows(com.dropit.global.exception.ServiceException.class,
                () -> query.read(RankingRange.TOTAL, YearMonth.parse("2026-09"), 10, null));
        assertEquals(RankingErrorCode.RANKING_UNAVAILABLE, exception.getErrorCode());
    }

    private boolean publish(Instant startedAt, List<SellerSales> sales) {
        return store.publish(execution(AS_OF, startedAt), startedAt, sales);
    }

    private SellerRankingExecution execution(Instant asOf, Instant startedAt) {
        return new SellerRankingExecution(UUID.randomUUID(),
                YearMonth.from(asOf.atZone(java.time.ZoneId.of("Asia/Seoul"))), asOf, startedAt);
    }
}
