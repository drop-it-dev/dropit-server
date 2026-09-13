package com.dropit.order.controller;

import com.dropit.documentation.DocumentationTestSupport;
import com.epages.restdocs.apispec.Schema;
import com.dropit.order.dto.response.OrderItemResponse;
import com.dropit.order.dto.response.OrderResponse;
import com.dropit.order.dto.response.OrderSummaryResponse;
import com.dropit.order.entity.OrderStatus;
import com.dropit.order.service.OrderService;
import com.dropit.order.service.OrderAdmissionService;
import com.dropit.order.service.OrderRequestQueryService;
import com.dropit.order.service.OrderCancellationService;
import com.dropit.order.redis.ReservedOrderSnapshot;
import com.dropit.order.entity.OrderRequestStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;

import java.math.BigDecimal;
import java.util.List;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceSnippetParameters.builder;
import static org.springframework.restdocs.payload.JsonFieldType.ARRAY;
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class OrderDocumentationTest extends DocumentationTestSupport {

    private OrderService orderService;
    private OrderAdmissionService admissionService;
    private OrderRequestQueryService queryService;
    private OrderCancellationService cancellationService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        orderService = mock(OrderService.class);
        admissionService = mock(OrderAdmissionService.class);
        queryService = mock(OrderRequestQueryService.class);
        cancellationService = mock(OrderCancellationService.class);
        configure(restDocumentation,
                new OrderController(orderService, admissionService, queryService, cancellationService));
    }

    @Test
    void create() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(admissionService.admit(eq(1L), eq("order-key"), any())).thenReturn(new ReservedOrderSnapshot(
                requestId, 1L, 100L, "hash", "100:2", 2, Instant.now(),
                "Limited Hoodie", new BigDecimal("59000"), 20,
                "CONFIRMED", OrderRequestStatus.PENDING, null, null,
                Instant.now().plusSeconds(86400).toEpochMilli()));
        mockMvc.perform(authenticated(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "order-key")
                        .content("{\"items\":[{\"dropId\":100,\"quantity\":2}]}")))
                .andExpect(status().isAccepted())
                .andDo(document("orders-create", resource(builder()
                        .tag("Orders").summary("주문 접수").description("Redis 예약 후 SQS가 수락한 주문 요청을 비동기로 접수합니다.")
                        .requestHeaders(authorizationHeader(),
                                org.springframework.restdocs.headers.HeaderDocumentation.headerWithName("Idempotency-Key")
                                        .description("주문 재전송을 식별하는 멱등키"))
                        .requestSchema(new Schema("OrderCreateRequest"))
                        .requestFields(
                                fieldWithPath("items").type(ARRAY).description("주문 항목 목록 (현재 한 항목만 허용)"),
                                fieldWithPath("items[].dropId").type(NUMBER).description("주문할 드랍 ID"),
                                fieldWithPath("items[].quantity").type(NUMBER).description("주문 수량")
                        ).responseSchema(new Schema("OrderAdmissionResponse")).responseFields(
                                fieldWithPath("requestId").type(STRING).description("주문 요청 ID")
                        ).build())));
    }

    @Test
    void getMyOrders() throws Exception {
        when(orderService.getMyOrders(1L)).thenReturn(List.of(new OrderSummaryResponse(1000L, OrderStatus.ORDERED, new BigDecimal("94400"), null)));
        mockMvc.perform(authenticated(get("/orders/me")))
                .andExpect(status().isOk())
                .andDo(document("orders-get-me", resource(builder()
                        .tag("Orders").summary("내 주문 목록 조회").requestHeaders(authorizationHeader())
                        .responseSchema(new Schema("OrderSummaryList"))
                        .responseFields(
                                fieldWithPath("[]").type(ARRAY).description("주문 요약 목록"),
                                fieldWithPath("[].id").type(NUMBER).description("주문 ID"),
                                fieldWithPath("[].status").type(STRING).description("주문 상태"),
                                fieldWithPath("[].totalPrice").type(NUMBER).description("총 주문 금액"),
                                fieldWithPath("[].createdAt").type(STRING).optional().description("주문 생성 시각")
                        ).build())));
    }

    @Test
    void getMyOrder() throws Exception {
        when(orderService.getMyOrder(1L, 1000L)).thenReturn(orderResponse());
        mockMvc.perform(authenticated(get("/orders/{orderId}", 1000L)))
                .andExpect(status().isOk())
                .andDo(document("orders-get", resource(builder()
                        .tag("Orders").summary("내 주문 상세 조회").requestHeaders(authorizationHeader())
                        .pathParameters(parameterWithName("orderId").description("주문 ID"))
                        .responseSchema(new Schema("OrderResponse")).responseFields(orderFields()).build())));
    }

    @Test
    void cancel() throws Exception {
        mockMvc.perform(authenticated(post("/orders/{orderId}/cancel", 1000L)))
                .andExpect(status().isNoContent())
                .andDo(document("orders-cancel", resource(builder()
                        .tag("Orders").summary("주문 취소").description("현재 사용자의 주문을 취소합니다.").requestHeaders(authorizationHeader())
                        .pathParameters(parameterWithName("orderId").description("주문 ID")).build())));
    }

    private OrderResponse orderResponse() {
        return new OrderResponse(1000L, OrderStatus.ORDERED, new BigDecimal("94400"), null,
                List.of(new OrderItemResponse(2000L, 100L, "Limited Hoodie", new BigDecimal("59000"), 20, 2, new BigDecimal("94400"))));
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] orderFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[]{
                fieldWithPath("id").type(NUMBER).description("주문 ID"),
                fieldWithPath("status").type(STRING).description("주문 상태"),
                fieldWithPath("totalPrice").type(NUMBER).description("총 주문 금액"),
                fieldWithPath("createdAt").type(STRING).optional().description("주문 생성 시각"),
                fieldWithPath("items").type(ARRAY).description("주문 항목 목록"),
                fieldWithPath("items[].id").type(NUMBER).description("주문 항목 ID"),
                fieldWithPath("items[].dropId").type(NUMBER).description("주문한 드랍 ID"),
                fieldWithPath("items[].productName").type(STRING).description("주문 당시 상품명"),
                fieldWithPath("items[].unitPrice").type(NUMBER).description("주문 당시 단가"),
                fieldWithPath("items[].discountRate").type(NUMBER).description("주문 당시 할인율"),
                fieldWithPath("items[].quantity").type(NUMBER).description("주문 수량"),
                fieldWithPath("items[].itemTotalPrice").type(NUMBER).description("주문 항목 금액")
        };
    }
}
