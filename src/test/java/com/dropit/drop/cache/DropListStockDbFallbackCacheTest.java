package com.dropit.drop.cache;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropListStockDbFallbackCacheTest {

    @Test
    void reusesDatabaseStockOnlyUntilTwoHundredMilliseconds() {
        AtomicLong now = new AtomicLong();
        AtomicInteger loads = new AtomicInteger();
        DropListStockDbFallbackCache cache = new DropListStockDbFallbackCache(now::get);
        List<Long> dropIds = List.of(1L);

        assertEquals(Map.of(1L, 10), cache.get(dropIds, () -> {
            loads.incrementAndGet();
            return Map.of(1L, 10);
        }));

        now.set(TimeUnit.MILLISECONDS.toNanos(199));
        assertEquals(Map.of(1L, 10), cache.get(dropIds, () -> {
            loads.incrementAndGet();
            return Map.of(1L, 9);
        }));

        now.set(TimeUnit.MILLISECONDS.toNanos(200));
        assertEquals(Map.of(1L, 9), cache.get(dropIds, () -> {
            loads.incrementAndGet();
            return Map.of(1L, 9);
        }));
        assertEquals(2, loads.get());
    }

    @Test
    void doesNotReuseStockForDifferentDropIds() {
        DropListStockDbFallbackCache cache = new DropListStockDbFallbackCache();
        AtomicInteger loads = new AtomicInteger();

        cache.get(List.of(1L), () -> {
            loads.incrementAndGet();
            return Map.of(1L, 10);
        });
        assertEquals(Map.of(2L, 8), cache.get(List.of(2L), () -> {
            loads.incrementAndGet();
            return Map.of(2L, 8);
        }));
        assertEquals(2, loads.get());
    }

    @Test
    void concurrentOutageRequestsShareOneDatabaseLoad() throws Exception {
        DropListStockDbFallbackCache cache = new DropListStockDbFallbackCache();
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            @SuppressWarnings("unchecked")
            Future<Map<Long, Integer>>[] results = new Future[8];
            for (int i = 0; i < results.length; i++) {
                results[i] = executor.submit(() -> {
                    start.await();
                    return cache.get(List.of(1L), () -> {
                        loads.incrementAndGet();
                        return Map.of(1L, 10);
                    });
                });
            }
            start.countDown();
            for (Future<Map<Long, Integer>> result : results) {
                assertEquals(Map.of(1L, 10), result.get(5, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, loads.get());
    }
}
