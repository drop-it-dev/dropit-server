package com.dropit.drop.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropListCacheMetricsTest {

    @Test
    void recordsCacheRequestsLoadsAndStockFallbacks() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DropListCacheMetrics metrics = new DropListCacheMetrics(meterRegistry);

        metrics.recordRequest();
        metrics.recordRequest();
        metrics.recordLoad();
        metrics.recordStockFallback();
        metrics.recordLocalFallback();

        assertEquals(2.0, meterRegistry.counter("drop.list.cache.requests").count());
        assertEquals(1.0, meterRegistry.counter("drop.list.cache.loads").count());
        assertEquals(1.0, meterRegistry.counter("drop.list.stock.fallbacks").count());
        assertEquals(1.0, meterRegistry.counter("drop.list.cache.local.fallbacks").count());
    }
}
