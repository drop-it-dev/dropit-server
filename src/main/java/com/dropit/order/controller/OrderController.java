package com.dropit.order.controller;

import com.dropit.global.security.principal.CurrentUserId;
import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.response.OrderResponse;
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

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        OrderResponse response = orderService.create(userId, request);

        return ResponseEntity
                .created(URI.create("/orders/" + response.id()))
                .body(response);
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
        orderService.cancel(userId, orderId);

        return ResponseEntity.noContent().build();
    }
}
