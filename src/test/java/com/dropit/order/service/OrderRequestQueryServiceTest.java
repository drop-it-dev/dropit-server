package com.dropit.order.service;

import com.dropit.order.entity.Order;
import com.dropit.order.entity.OrderRequest;
import com.dropit.order.entity.OrderRequestStatus;
import com.dropit.order.redis.RedisOrderAdmissionAdapter;
import com.dropit.order.redis.ReservedOrderSnapshot;
import com.dropit.order.repository.OrderRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OrderRequestQueryServiceTest {

    private final OrderRequestRepository repository = mock(OrderRequestRepository.class);
    private final RedisOrderAdmissionAdapter redisAdapter = mock(RedisOrderAdmissionAdapter.class);
    private final OrderRequestQueryService service = new OrderRequestQueryService(repository, redisAdapter);

    @Test
    @DisplayName("DB terminal 결과가 있으면 Redis를 조회하지 않는다")
    void preferDatabaseTerminalResult() {
        UUID requestId = UUID.randomUUID();
        OrderRequest request = mock(OrderRequest.class);
        Order order = mock(Order.class);
        when(request.isTerminal()).thenReturn(true);
        when(request.getId()).thenReturn(requestId);
        when(request.getStatus()).thenReturn(OrderRequestStatus.SUCCEEDED);
        when(request.getOrder()).thenReturn(order);
        when(repository.findByIdAndUserId(requestId, 1L)).thenReturn(Optional.of(request));

        var response = service.get(1L, requestId);

        assertEquals(OrderRequestStatus.SUCCEEDED, response.status());
        verifyNoInteractions(redisAdapter);
    }

    @Test
    @DisplayName("DB 반영 전에는 본인 소유 Redis 예약을 PENDING으로 반환한다")
    void returnRedisPendingBeforeDatabaseRegistration() {
        UUID requestId = UUID.randomUUID();
        ReservedOrderSnapshot snapshot = mock(ReservedOrderSnapshot.class);
        when(snapshot.requestId()).thenReturn(requestId);
        when(snapshot.status()).thenReturn(OrderRequestStatus.PENDING);
        when(repository.findByIdAndUserId(requestId, 1L)).thenReturn(Optional.empty());
        when(redisAdapter.findByRequestId(requestId, 1L)).thenReturn(Optional.of(snapshot));

        var response = service.get(1L, requestId);

        assertEquals(OrderRequestStatus.PENDING, response.status());
    }
}
