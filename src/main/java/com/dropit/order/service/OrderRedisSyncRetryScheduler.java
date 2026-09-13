package com.dropit.order.service;

import com.dropit.order.entity.OrderRequest;
import com.dropit.order.repository.OrderRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRedisSyncRetryScheduler {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final OrderRequestRepository repository;
    private final OrderRequestRedisSyncService syncService;

    @Scheduled(fixedDelayString = "${app.order.redis-sync-retry-delay:5000}")
    public void retryPending() {
        for (OrderRequest request : repository
                .findTop100ByRedisSyncPendingTrueAndRedisSyncNextAttemptAtLessThanEqualOrderByRedisSyncNextAttemptAtAsc(
                        Instant.now())) {
            try {
                syncService.sync(request.getId());
            } catch (RuntimeException exception) {
                try {
                    syncService.defer(request.getId(), RETRY_DELAY);
                } catch (RuntimeException deferException) {
                    exception.addSuppressed(deferException);
                }
                log.warn("주문 Redis 동기화 재시도에 실패했습니다. requestId={}", request.getId(), exception);
            }
        }
    }
}
