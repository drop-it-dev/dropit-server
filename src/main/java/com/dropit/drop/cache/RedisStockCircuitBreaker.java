package com.dropit.drop.cache;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** A stock MGET timeout must not disable the separate list metadata cache. */
@Component
public class RedisStockCircuitBreaker {

    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    private final AtomicLong retryAtNanos = new AtomicLong();

    public boolean shouldAttemptRedis() {
        return System.nanoTime() >= retryAtNanos.get();
    }

    public void open() {
        retryAtNanos.set(System.nanoTime() + RETRY_INTERVAL.toNanos());
    }

    public void close() {
        retryAtNanos.set(0L);
    }
}
