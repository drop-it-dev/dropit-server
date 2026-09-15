package com.dropit.drop.cache;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Reuses only static list metadata; remaining stock is read separately each request. */
@Component
public class DropListLocalReadCache {

    static final Duration TTL = Duration.ofMillis(200);

    private final AtomicReference<Entry> latest = new AtomicReference<>();
    private final DropListCacheMetrics metrics;
    private final LongSupplier nanoTime;

    @Autowired
    public DropListLocalReadCache(DropListCacheMetrics metrics) {
        this(metrics, System::nanoTime);
    }

    DropListLocalReadCache(DropListCacheMetrics metrics, LongSupplier nanoTime) {
        this.metrics = metrics;
        this.nanoTime = nanoTime;
    }

    public DropListCacheValue get(Supplier<DropListCacheValue> loader) {
        Entry current = latest.get();
        if (isFresh(current)) {
            metrics.recordL1Hit();
            return current.value();
        }

        synchronized (this) {
            current = latest.get();
            if (isFresh(current)) {
                metrics.recordL1Hit();
                return current.value();
            }

            DropListCacheValue value = loader.get();
            latest.set(new Entry(value, nanoTime.getAsLong() + TTL.toNanos()));
            metrics.recordL1Load();
            return value;
        }
    }

    public void invalidate() {
        latest.set(null);
    }

    private boolean isFresh(Entry entry) {
        return entry != null && entry.expiresAtNanos() > nanoTime.getAsLong();
    }

    private record Entry(DropListCacheValue value, long expiresAtNanos) {
    }
}
