package com.dropit.order.controller;

import com.dropit.global.security.principal.CurrentUserId;
import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.response.OrderResponse;
import com.dropit.order.dto.response.OrderAdmissionResponse;
import com.dropit.order.dto.response.OrderRequestStatusResponse;
import com.dropit.order.redis.ReservedOrderSnapshot;
import com.dropit.order.service.OrderAdmissionService;
import com.dropit.order.service.OrderRequestQueryService;
import com.dropit.order.service.OrderCancellationService;
import com.dropit.order.dto.response.OrderSummaryResponse;
import com.dropit.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderAdmissionService orderAdmissionService;
    private final OrderRequestQueryService orderRequestQueryService;
    private final OrderCancellationService orderCancellationService;

    @PostMapping("/orders")
    public ResponseEntity<OrderAdmissionResponse> create(
            @CurrentUserId Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        ReservedOrderSnapshot admitted = orderAdmissionService.admit(userId, idempotencyKey, request);
        OrderAdmissionResponse response = new OrderAdmissionResponse(admitted.requestId());

        return ResponseEntity
                .accepted()
                .location(URI.create("/order-requests/" + response.requestId()))
                .body(response);
    }

    @GetMapping("/order-requests/{requestId}")
    public ResponseEntity<OrderRequestStatusResponse> getOrderRequest(
            @CurrentUserId Long userId,
            @PathVariable java.util.UUID requestId
    ) {
        return ResponseEntity.ok(orderRequestQueryService.get(userId, requestId));
    }

    @GetMapping("/orders/me")
    public ResponseEntity<List<OrderSummaryResponse>> getMyOrders(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(orderService.getMyOrders(userId));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getMyOrder(
            @CurrentUserId Long userId,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.getMyOrder(userId, orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancel(
            @CurrentUserId Long userId,
            @PathVariable Long orderId
    ) {
        orderCancellationService.cancel(userId, orderId);

        return ResponseEntity.noContent().build();
    }
}
