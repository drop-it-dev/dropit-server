package com.dropit.drop.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DropListCacheMetrics {

    private final Counter requestCounter;
    private final Counter loadCounter;
    private final Counter stockFallbackCounter;
    private final Counter stockDbLoadCounter;
    private final Counter localFallbackCounter;
    private final Counter l1HitCounter;
    private final Counter l1LoadCounter;

    public DropListCacheMetrics(MeterRegistry meterRegistry) {
        this.requestCounter = Counter.builder("drop.list.cache.requests")
                .description("캐시 대상 Drop 목록 요청 수")
                .register(meterRegistry);
        this.loadCounter = Counter.builder("drop.list.cache.loads")
                .description("Redis 캐시가 없어 DB에서 목록을 불러온 수")
                .register(meterRegistry);
        this.stockFallbackCounter = Counter.builder("drop.list.stock.fallbacks")
                .description("Redis 재고 조회 실패로 DB 재고를 사용한 수")
                .register(meterRegistry);
        this.stockDbLoadCounter = Counter.builder("drop.list.stock.db.loads")
                .description("Redis 장애 중 DB 재고를 실제로 조회한 수")
                .register(meterRegistry);
        this.localFallbackCounter = Counter.builder("drop.list.cache.local.fallbacks")
                .description("Redis 목록 장애로 서버의 마지막 정상 목록을 사용한 수")
                .register(meterRegistry);
        this.l1HitCounter = Counter.builder("drop.list.cache.l1.hits")
                .description("Spring 서버의 200ms 정적 목록 캐시 적중 수")
                .register(meterRegistry);
        this.l1LoadCounter = Counter.builder("drop.list.cache.l1.loads")
                .description("Spring 서버의 200ms 정적 목록 캐시 재조회 수")
                .register(meterRegistry);
    }

    public void recordRequest() {
        requestCounter.increment();
    }

    public void recordLoad() {
        loadCounter.increment();
    }

    public void recordStockFallback() {
        stockFallbackCounter.increment();
    }

    public void recordStockDbLoad() {
        stockDbLoadCounter.increment();
    }

    public void recordLocalFallback() {
        localFallbackCounter.increment();
    }

    public void recordL1Hit() {
        l1HitCounter.increment();
    }

    public void recordL1Load() {
        l1LoadCounter.increment();
    }
}
