package com.dropit.drop.cache;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component("redisCacheCircuitBreaker")
public class RedisCacheCircuitBreaker {

    static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    private final AtomicLong retryAtNanos = new AtomicLong();

    public boolean shouldAttemptRedis() {
        return System.nanoTime() >= retryAtNanos.get();
    }

    public boolean isOpen() {
        return !shouldAttemptRedis();
    }

    public void open() {
        retryAtNanos.set(System.nanoTime() + RETRY_INTERVAL.toNanos());
    }

    public void close() {
        retryAtNanos.set(0L);
    }
}
