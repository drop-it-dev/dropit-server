package com.dropit.order.controller;

import com.dropit.global.exception.GlobalExceptionHandler;
import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.response.OrderResponse;
import com.dropit.order.dto.response.OrderSummaryResponse;
import com.dropit.order.entity.OrderStatus;
import com.dropit.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private MockMvc mockMvc;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("배열 형태의 주문 항목으로 주문을 생성한다")
    void createOrder() throws Exception {
        OrderResponse response = new OrderResponse(
                1000L,
                OrderStatus.ORDERED,
                new BigDecimal("94400"),
                null,
                List.of()
        );
        when(orderService.create(eq(1L), any(OrderCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/orders")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"dropId": 100, "quantity": 2}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/1000"))
                .andExpect(jsonPath("$.id").value(1000L))
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.totalPrice").value(94400));

        ArgumentCaptor<OrderCreateRequest> captor = ArgumentCaptor.forClass(OrderCreateRequest.class);
        verify(orderService).create(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().items().size());
        assertEquals(100L, captor.getValue().items().getFirst().dropId());
        assertEquals(2, captor.getValue().items().getFirst().quantity());
    }

    @Test
    @DisplayName("현재 MVP에서는 주문 항목을 두 개 이상 요청할 수 없다")
    void rejectMultipleOrderItems() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"dropId": 100, "quantity": 1},
                                    {"dropId": 101, "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("내 주문 목록을 조회한다")
    void getMyOrders() throws Exception {
        when(orderService.getMyOrders(1L)).thenReturn(List.of(
                new OrderSummaryResponse(1000L, OrderStatus.ORDERED, new BigDecimal("94400"), null)
        ));

        mockMvc.perform(get("/orders/me").header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1000L))
                .andExpect(jsonPath("$[0].status").value("ORDERED"));
    }

    @Test
    @DisplayName("내 주문 상세를 조회한다")
    void getMyOrder() throws Exception {
        when(orderService.getMyOrder(1L, 1000L)).thenReturn(
                new OrderResponse(1000L, OrderStatus.ORDERED, new BigDecimal("94400"), null, List.of())
        );

        mockMvc.perform(get("/orders/{orderId}", 1000L).header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1000L))
                .andExpect(jsonPath("$.totalPrice").value(94400));
    }

    @Test
    @DisplayName("내 주문을 취소한다")
    void cancelOrder() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/cancel", 1000L).header("X-User-Id", 1L))
                .andExpect(status().isNoContent());

        verify(orderService).cancel(1L, 1000L);
    }
}
