package com.dropit.drop.cache;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropListLocalFallbackTest {

    @Test
    void remembersLatestSuccessfulListInsideLocalServer() {
        DropListLocalFallback fallback = new DropListLocalFallback();
        DropListCacheValue value = new DropListCacheValue(List.of(), 0);

        fallback.remember(value);

        assertTrue(fallback.findFresh().isPresent());
        assertEquals(value, fallback.findFresh().orElseThrow());
    }

    @Test
    void returningSameLocalValueDoesNotReplaceStoredEntry() {
        AtomicLong now = new AtomicLong();
        DropListLocalFallback fallback = new DropListLocalFallback(now::get);
        DropListCacheValue value = new DropListCacheValue(List.of(), 0);

        fallback.remember(value);
        DropListCacheValue stored = fallback.findFresh().orElseThrow();
        now.addAndGet(DropListLocalFallback.TTL.minusSeconds(10).toNanos());
        fallback.remember(stored);
        now.addAndGet(java.time.Duration.ofSeconds(11).toNanos());

        assertTrue(fallback.findFresh().isEmpty());
    }
}
