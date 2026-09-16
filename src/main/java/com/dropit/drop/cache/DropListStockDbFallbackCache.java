package com.dropit.drop.cache;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Component
public class DropListStockDbFallbackCache {

    static final Duration TTL = Duration.ofMillis(200);

    private final LongSupplier nanoTime;
    private Entry latest;

    public DropListStockDbFallbackCache() {
        this(System::nanoTime);
    }

    DropListStockDbFallbackCache(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public synchronized Map<Long, Integer> get(
            List<Long> dropIds,
            Supplier<Map<Long, Integer>> databaseLoad
    ) {
        List<Long> key = List.copyOf(dropIds);
        long now = nanoTime.getAsLong();
        if (latest != null
                && latest.dropIds().equals(key)
                && now < latest.expiresAtNanos()) {
            return latest.quantities();
        }

        Map<Long, Integer> quantities = Map.copyOf(databaseLoad.get());
        if (quantities.size() == key.size()) {
            latest = new Entry(key, quantities, nanoTime.getAsLong() + TTL.toNanos());
        }
        return quantities;
    }

    private record Entry(
            List<Long> dropIds,
            Map<Long, Integer> quantities,
            long expiresAtNanos
    ) {
    }
}
