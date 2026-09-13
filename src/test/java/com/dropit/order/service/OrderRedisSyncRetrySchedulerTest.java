package com.dropit.order.service;

import com.dropit.order.entity.OrderRequest;
import com.dropit.order.repository.OrderRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderRedisSyncRetrySchedulerTest {

    @Test
    @DisplayName("한 요청의 실패를 다음 시각으로 미루고 같은 배치의 다음 요청을 계속 처리한다")
    void deferFailureAndContinueBatch() {
        OrderRequestRepository repository = mock(OrderRequestRepository.class);
        OrderRequestRedisSyncService syncService = mock(OrderRequestRedisSyncService.class);
        OrderRequest first = mock(OrderRequest.class);
        OrderRequest second = mock(OrderRequest.class);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(first.getId()).thenReturn(firstId);
        when(second.getId()).thenReturn(secondId);
        when(repository
                .findTop100ByRedisSyncPendingTrueAndRedisSyncNextAttemptAtLessThanEqualOrderByRedisSyncNextAttemptAtAsc(
                        any(Instant.class)))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("redis unavailable")).when(syncService).sync(firstId);
        doThrow(new IllegalStateException("database unavailable"))
                .when(syncService).defer(firstId, Duration.ofSeconds(5));

        new OrderRedisSyncRetryScheduler(repository, syncService).retryPending();

        verify(syncService).defer(firstId, Duration.ofSeconds(5));
        verify(syncService).sync(secondId);
    }
}
