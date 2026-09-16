package com.dropit.drop.cache;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

@Component
public class DropListLocalFallback {

    static final Duration TTL = Duration.ofMinutes(1);

    private final AtomicReference<Entry> latest = new AtomicReference<>();
    private final LongSupplier nanoTime;

    public DropListLocalFallback() {
        this(System::nanoTime);
    }

    DropListLocalFallback(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public void remember(DropListCacheValue value) {
        latest.updateAndGet(current -> {
            if (current != null && current.value() == value) {
                return current;
            }
            return new Entry(value, nanoTime.getAsLong() + TTL.toNanos());
        });
    }

    public Optional<DropListCacheValue> findFresh() {
        Entry entry = latest.get();
        if (entry == null || entry.expiresAtNanos() <= nanoTime.getAsLong()) {
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    private record Entry(
            DropListCacheValue value,
            long expiresAtNanos
    ) {
    }
}
