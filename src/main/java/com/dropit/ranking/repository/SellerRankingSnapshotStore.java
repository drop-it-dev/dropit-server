package com.dropit.ranking.repository;

import com.dropit.ranking.batch.SellerRankingExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class SellerRankingSnapshotStore {

    public static final String TOTAL_KEY = "ranking:{seller}:total";
    public static final String METADATA_KEY = "ranking:{seller}:metadata";
    private static final int CHUNK_SIZE = 500;
    private static final DefaultRedisScript<Long> STAGE = new DefaultRedisScript<>("""
            for i = 2, #ARGV, 2 do
                redis.call('ZADD', KEYS[1], ARGV[i], ARGV[i + 1])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return redis.call('ZCARD', KEYS[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> PUBLISH = new DefaultRedisScript<>();

    static {
        PUBLISH.setLocation(new ClassPathResource("redis/ranking/publish-seller-ranking.lua"));
        PUBLISH.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;

    public boolean publish(SellerRankingExecution execution, Instant publishedAt, List<SellerSales> sales) {
        if (execution.asOf().isAfter(execution.startedAt()) || execution.startedAt().isAfter(publishedAt)) {
            throw new IllegalArgumentException("집계 시각의 순서가 올바르지 않습니다.");
        }
        var sellerIds = new HashSet<Long>();
        for (SellerSales sale : sales) {
            if (!sellerIds.add(sale.sellerId())) {
                throw new IllegalArgumentException("판매자 집계 결과가 중복되었습니다.");
            }
        }
        String month = execution.month().toString();
        String staging = "ranking:{seller}:staging:" + UUID.randomUUID();
        String totalStaging = staging + ":total";
        String monthlyStaging = staging + ":monthly";
        stage(totalStaging, sales, false);
        stage(monthlyStaging, sales, true);
        long monthlyCount = sales.stream().filter(sale -> sale.monthlyQuantity() > 0).count();

        Long result = redis.execute(PUBLISH,
                List.of(totalStaging, monthlyStaging, TOTAL_KEY, monthlyKey(execution.month()),
                        METADATA_KEY, SellerRankingExecutionStore.executionKey(execution.version().toString())),
                Long.toString(execution.asOf().toEpochMilli()),
                Long.toString(execution.startedAt().toEpochMilli()),
                execution.asOf().toString(), publishedAt.toString(), month,
                execution.version().toString(),
                Integer.toString(sales.size()), Long.toString(monthlyCount));
        if (result == null) {
            throw new IllegalStateException("랭킹 스냅샷 발행 결과를 확인하지 못했습니다.");
        }
        return result == 1;
    }

    public static String monthlyKey(java.time.YearMonth month) {
        return "ranking:{seller}:monthly:" + month;
    }

    private void stage(String key, List<SellerSales> sales, boolean monthly) {
        var args = new ArrayList<String>();
        args.add("3600");
        for (SellerSales sale : sales) {
            long quantity = monthly ? sale.monthlyQuantity() : sale.totalQuantity();
            if (quantity == 0) {
                continue;
            }
            args.add(Long.toString(quantity));
            args.add(Long.toString(sale.sellerId()));
            if (args.size() == 1 + CHUNK_SIZE * 2) {
                redis.execute(STAGE, List.of(key), args.toArray());
                args.subList(1, args.size()).clear();
            }
        }
        if (args.size() > 1) {
            redis.execute(STAGE, List.of(key), args.toArray());
        }
    }
}
