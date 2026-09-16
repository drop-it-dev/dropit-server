package com.dropit.ranking.repository;

import com.dropit.ranking.batch.SellerRankingExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class SellerRankingExecutionStore {

    public static final String LOCK_KEY = "ranking:{seller}:execution-lock";
    public static final String HISTORY_KEY = "ranking:{seller}:executions";
    private static final Duration HISTORY_TTL = Duration.ofDays(7);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>();

    static {
        RELEASE_LOCK.setLocation(new ClassPathResource("redis/ranking/release-seller-ranking-lock.lua"));
        RELEASE_LOCK.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final Duration lockTtl;

    public boolean tryAcquire(SellerRankingExecution execution) {
        return Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(LOCK_KEY, execution.version().toString(), lockTtl));
    }

    public void recordStarted(SellerRankingExecution execution) {
        String version = execution.version().toString();
        String key = executionKey(version);
        redis.opsForHash().putAll(key, Map.of(
                "version", version,
                "ranges", "TOTAL,MONTHLY",
                "targetMonth", execution.month().toString(),
                "asOf", execution.asOf().toString(),
                "startedAt", execution.startedAt().toString(),
                "status", "STARTED"
        ));
        redis.expire(key, HISTORY_TTL);
        redis.opsForZSet().add(HISTORY_KEY, version, execution.startedAt().toEpochMilli());
        redis.expire(HISTORY_KEY, HISTORY_TTL);
    }

    public void recordSkipped(SellerRankingExecution execution, Instant completedAt, String reason) {
        recordTerminal(execution, completedAt, "SKIPPED", reason);
    }

    public void recordFailed(SellerRankingExecution execution, Instant completedAt, Throwable failure) {
        String reason = failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "메시지 없음" : failure.getMessage());
        recordTerminal(execution, completedAt, "FAILED", reason);
    }

    public void release(SellerRankingExecution execution) {
        redis.execute(RELEASE_LOCK, List.of(LOCK_KEY), execution.version().toString());
    }

    public static String executionKey(String version) {
        return "ranking:{seller}:execution:" + version;
    }

    private void recordTerminal(SellerRankingExecution execution, Instant completedAt,
                                String status, String reason) {
        String version = execution.version().toString();
        String key = executionKey(version);
        redis.opsForHash().putAll(key, Map.of(
                "version", version,
                "ranges", "TOTAL,MONTHLY",
                "targetMonth", execution.month().toString(),
                "asOf", execution.asOf().toString(),
                "startedAt", execution.startedAt().toString(),
                "completedAt", completedAt.toString(),
                "status", status,
                "reason", abbreviate(reason)
        ));
        redis.expire(key, HISTORY_TTL);
        redis.opsForZSet().add(HISTORY_KEY, version, execution.startedAt().toEpochMilli());
        redis.expire(HISTORY_KEY, HISTORY_TTL);
    }

    private String abbreviate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
