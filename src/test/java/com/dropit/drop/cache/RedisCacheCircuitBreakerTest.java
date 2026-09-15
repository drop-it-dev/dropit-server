package com.dropit.drop.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisCacheCircuitBreakerTest {

    @Test
    void skipsRedisAfterFailureAndAllowsItAgainAfterClose() {
        RedisCacheCircuitBreaker circuitBreaker = new RedisCacheCircuitBreaker();

        assertTrue(circuitBreaker.shouldAttemptRedis());

        circuitBreaker.open();
        assertFalse(circuitBreaker.shouldAttemptRedis());
        assertTrue(circuitBreaker.isOpen());

        circuitBreaker.close();
        assertTrue(circuitBreaker.shouldAttemptRedis());
    }

    @Test
    void stockFailureDoesNotOpenListMetadataCircuit() {
        RedisCacheCircuitBreaker metadataCircuit = new RedisCacheCircuitBreaker();
        RedisStockCircuitBreaker stockCircuit = new RedisStockCircuitBreaker();

        stockCircuit.open();

        assertFalse(stockCircuit.shouldAttemptRedis());
        assertTrue(metadataCircuit.shouldAttemptRedis());
    }

}
