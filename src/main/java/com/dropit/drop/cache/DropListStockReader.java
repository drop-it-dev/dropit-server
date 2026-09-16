package com.dropit.drop.cache;

import com.dropit.drop.repository.DropRepository;
import com.dropit.order.redis.RedisOrderKeyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DropListStockReader {

    private final AtomicBoolean fallbackWarningLogged = new AtomicBoolean();

    private final StringRedisTemplate redisTemplate;
    private final DropRepository dropRepository;
    private final DropListCacheMetrics cacheMetrics;
    private final RedisStockCircuitBreaker circuitBreaker;
    private final DropListStockDbFallbackCache dbFallbackCache;

    public Map<Long, Integer> getRemainingQuantities(List<Long> dropIds) {
        if (dropIds.isEmpty()) {
            return Map.of();
        }

        if (!circuitBreaker.shouldAttemptRedis()) {
            cacheMetrics.recordStockFallback();
            return loadFromDatabaseDuringOutage(dropIds);
        }

        try {
            Map<Long, Integer> result = readRedisAndFillMissingFromDatabase(dropIds);
            circuitBreaker.close();
            fallbackWarningLogged.set(false);
            return result;
        } catch (RuntimeException exception) {
            circuitBreaker.open();
            cacheMetrics.recordStockFallback();
            if (fallbackWarningLogged.compareAndSet(false, true)) {
                log.warn("목록 재고 Redis 조회에 실패해 DB 재고를 사용합니다. cause={}",
                        exception.toString());
            }
            return loadFromDatabaseDuringOutage(dropIds);
        }
    }

    private Map<Long, Integer> loadFromDatabaseDuringOutage(List<Long> dropIds) {
        return dbFallbackCache.get(dropIds, () -> {
            cacheMetrics.recordStockDbLoad();
            return dropRepository.findRemainingQuantitiesByIds(dropIds);
        });
    }

    private Map<Long, Integer> readRedisAndFillMissingFromDatabase(List<Long> dropIds) {
        List<String> stockKeys = dropIds.stream()
                .map(RedisOrderKeyFactory::stockKey)
                .toList();
        List<String> stockValues = redisTemplate.opsForValue().multiGet(stockKeys);

        Map<Long, Integer> remainingQuantities = new HashMap<>();
        if (stockValues != null) {
            for (int index = 0; index < stockValues.size(); index++) {
                String stockValue = stockValues.get(index);
                Integer remainingQuantity = parseStock(stockValue);
                if (remainingQuantity != null) {
                    remainingQuantities.put(dropIds.get(index), remainingQuantity);
                }
            }
        }

        List<Long> missingDropIds = dropIds.stream()
                .filter(dropId -> !remainingQuantities.containsKey(dropId))
                .toList();
        if (!missingDropIds.isEmpty()) {
            remainingQuantities.putAll(
                    dropRepository.findRemainingQuantitiesByIds(missingDropIds)
            );
        }

        return remainingQuantities;
    }

    private Integer parseStock(String stockValue) {
        if (stockValue == null) {
            return null;
        }

        try {
            int remainingQuantity = Integer.parseInt(stockValue);
            return remainingQuantity >= 0 ? remainingQuantity : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
