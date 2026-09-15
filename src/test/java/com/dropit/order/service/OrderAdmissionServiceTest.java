package com.dropit.order.service;

import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.request.OrderItemCreateRequest;
import com.dropit.order.messaging.OrderMessagePublisher;
import com.dropit.order.redis.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderAdmissionServiceTest {

    private final RedisOrderAdmissionAdapter redisAdapter = mock(RedisOrderAdmissionAdapter.class);
    private final OrderMessagePublisher publisher = mock(OrderMessagePublisher.class);
    private final OrderAdmissionService service = new OrderAdmissionService(redisAdapter, publisher);

    @Test
    @DisplayName("신규 예약은 원본 멱등키를 SHA-256으로 바꾼 뒤 snapshot 메시지를 발행한다")
    void publishNewReservationWithoutRawKey() {
        UUID requestId = UUID.randomUUID();
        when(redisAdapter.reserve(any())).thenReturn(OrderAdmissionResult.newReservation(requestId));
        when(redisAdapter.getSnapshot(eq(10L), eq(1L), any())).thenAnswer(invocation ->
                snapshot(requestId, invocation.getArgument(2), "UNCONFIRMED"));

        ReservedOrderSnapshot result = service.admit(1L, " raw-key ", request());

        ArgumentCaptor<OrderAdmissionRequest> command = ArgumentCaptor.forClass(OrderAdmissionRequest.class);
        verify(redisAdapter).reserve(command.capture());
        assertAll(
                () -> assertEquals(requestId, result.requestId()),
                () -> assertEquals(64, command.getValue().idempotencyKeyHash().length()),
                () -> assertNotEquals(" raw-key ", command.getValue().idempotencyKeyHash()),
                () -> assertEquals("10:2", command.getValue().fingerprint())
        );
        verify(redisAdapter).indexRequest(result);
        verify(publisher).publish(result.toMessage());
        verify(redisAdapter).confirmPublication(result);
    }

    @Test
    @DisplayName("발행 확인된 replay는 같은 requestId를 반환하고 다시 발행하지 않는다")
    void doNotRepublishConfirmedReplay() {
        UUID requestId = UUID.randomUUID();
        when(redisAdapter.reserve(any())).thenReturn(OrderAdmissionResult.replay(requestId));
        when(redisAdapter.getSnapshot(eq(10L), eq(1L), any())).thenAnswer(invocation ->
                snapshot(requestId, invocation.getArgument(2), "CONFIRMED"));

        ReservedOrderSnapshot result = service.admit(1L, "key", request());

        assertEquals(requestId, result.requestId());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("SQS 발행 결과 불명은 예약을 복원하지 않고 같은 requestId를 전달한다")
    void keepReservationWhenPublicationIsUnconfirmed() {
        UUID requestId = UUID.randomUUID();
        when(redisAdapter.reserve(any())).thenReturn(OrderAdmissionResult.newReservation(requestId));
        when(redisAdapter.getSnapshot(eq(10L), eq(1L), any())).thenAnswer(invocation ->
                snapshot(requestId, invocation.getArgument(2), "UNCONFIRMED"));
        doThrow(new IllegalStateException("timeout")).when(publisher).publish(any());

        OrderPublicationUnconfirmedException exception = assertThrows(
                OrderPublicationUnconfirmedException.class,
                () -> service.admit(1L, "key", request()));

        assertEquals(requestId, exception.getRequestId());
        verify(redisAdapter, never()).confirmPublication(any());
    }

    private OrderCreateRequest request() {
        return new OrderCreateRequest(List.of(new OrderItemCreateRequest(10L, 2)));
    }

    private ReservedOrderSnapshot snapshot(UUID requestId, String keyHash, String publicationState) {
        return new ReservedOrderSnapshot(
                requestId, 1L, 10L, keyHash, "10:2", 2, Instant.now(),
                "product", new BigDecimal("1000"), 10, publicationState,
                com.dropit.order.entity.OrderRequestStatus.PENDING, null, null,
                Instant.now().plusSeconds(3600).toEpochMilli()
        );
    }
}
