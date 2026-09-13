package com.dropit.order.controller;

import com.dropit.global.exception.GlobalExceptionHandler;
import com.dropit.global.security.authentication.JwtAuthenticationToken;
import com.dropit.global.security.principal.AuthUser;
import com.dropit.order.dto.response.OrderRequestStatusResponse;
import com.dropit.order.entity.OrderRequestStatus;
import com.dropit.order.redis.ReservedOrderSnapshot;
import com.dropit.order.service.OrderAdmissionService;
import com.dropit.order.service.OrderCancellationService;
import com.dropit.order.service.OrderRequestQueryService;
import com.dropit.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest {

    private MockMvc mockMvc;
    private OrderService orderService;
    private OrderAdmissionService admissionService;
    private OrderRequestQueryService queryService;
    private OrderCancellationService cancellationService;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        admissionService = mock(OrderAdmissionService.class);
        queryService = mock(OrderRequestQueryService.class);
        cancellationService = mock(OrderCancellationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OrderController(orderService, admissionService, queryService, cancellationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(new AuthUser(1L), List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Redis 예약과 SQS 발행이 확인되면 같은 requestId로 202를 반환한다")
    void admitOrder() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(admissionService.admit(eq(1L), eq("key-1"), any())).thenReturn(snapshot(requestId));

        mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"dropId\":100,\"quantity\":2}]}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/order-requests/" + requestId))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
    }

    @Test
    @DisplayName("여러 Drop 주문은 admission을 호출하기 전에 거절한다")
    void rejectMultipleItems() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"dropId\":100,\"quantity\":1},{\"dropId\":101,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(admissionService);
    }

    @Test
    @DisplayName("본인의 주문 요청 상태를 조회한다")
    void getOrderRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(queryService.get(1L, requestId)).thenReturn(
                new OrderRequestStatusResponse(requestId, OrderRequestStatus.PENDING, null, null, null));

        mockMvc.perform(get("/order-requests/{requestId}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("주문 취소는 취소 조정 서비스에 위임한다")
    void cancelOrder() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/cancel", 1000L))
                .andExpect(status().isNoContent());
        verify(cancellationService).cancel(1L, 1000L);
    }

    private ReservedOrderSnapshot snapshot(UUID requestId) {
        return new ReservedOrderSnapshot(
                requestId, 1L, 100L, "hash", "100:2", 2, Instant.now(),
                "Limited Hoodie", new BigDecimal("59000"), 20,
                "CONFIRMED", OrderRequestStatus.PENDING, null, null,
                Instant.now().plusSeconds(86_400).toEpochMilli()
        );
    }
}
