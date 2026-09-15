package com.dropit.drop.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropListLocalReadCacheTest {

    @Test
    void refreshesStaticMetadataAfterTwoHundredMilliseconds() {
        AtomicLong now = new AtomicLong();
        AtomicInteger loads = new AtomicInteger();
        DropListLocalReadCache cache = new DropListLocalReadCache(
                new DropListCacheMetrics(new SimpleMeterRegistry()), now::get
        );
        DropListCacheValue original = new DropListCacheValue(List.of(), 1);
        DropListCacheValue changed = new DropListCacheValue(List.of(), 2);

        assertEquals(original, cache.get(() -> {
            loads.incrementAndGet();
            return original;
        }));
        now.set(TimeUnit.MILLISECONDS.toNanos(199));
        assertEquals(original, cache.get(() -> {
            loads.incrementAndGet();
            return changed;
        }));
        now.set(TimeUnit.MILLISECONDS.toNanos(200));
        assertEquals(changed, cache.get(() -> {
            loads.incrementAndGet();
            return changed;
        }));
        assertEquals(2, loads.get());
    }

    @Test
    void concurrentRequestsShareOneMetadataRefresh() throws Exception {
        DropListLocalReadCache cache = new DropListLocalReadCache(
                new DropListCacheMetrics(new SimpleMeterRegistry())
        );
        DropListCacheValue value = new DropListCacheValue(List.of(), 1);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            @SuppressWarnings("unchecked")
            Future<DropListCacheValue>[] results = new Future[8];
            for (int index = 0; index < results.length; index++) {
                results[index] = executor.submit(() -> {
                    start.await();
                    return cache.get(() -> {
                        loads.incrementAndGet();
                        return value;
                    });
                });
            }
            start.countDown();
            for (Future<DropListCacheValue> result : results) {
                assertEquals(value, result.get(5, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, loads.get());
    }
}
