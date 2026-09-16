package com.dropit.drop.cache;

import org.springframework.stereotype.Component;

@Component
public class CacheReadFailureContext {

    private final ThreadLocal<Boolean> failed = ThreadLocal.withInitial(() -> false);

    public void markFailed() {
        failed.set(true);
    }

    public boolean consumeFailure() {
        boolean result = failed.get();
        failed.remove();
        return result;
    }
}
