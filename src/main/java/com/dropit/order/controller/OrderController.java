package com.dropit.order.controller;

import com.dropit.order.dto.request.OrderCreateRequest;
import com.dropit.order.dto.response.OrderResponse;
import com.dropit.order.dto.response.OrderSummaryResponse;
import com.dropit.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> create(
            @RequestHeader("X-User-Id") Long userId, // TODO: 인증 적용 후 SecurityContext에서 추출
            @Valid @RequestBody OrderCreateRequest request
    ) {
        OrderResponse response = orderService.create(userId, request);

        return ResponseEntity
                .created(URI.create("/orders/" + response.id()))
                .body(response);
    }

    @GetMapping("/orders/me")
    public ResponseEntity<List<OrderSummaryResponse>> getMyOrders(
            @RequestHeader("X-User-Id") Long userId // TODO: 인증 적용 후 SecurityContext에서 추출
    ) {
        return ResponseEntity.ok(orderService.getMyOrders(userId));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getMyOrder(
            @RequestHeader("X-User-Id") Long userId, // TODO: 인증 적용 후 SecurityContext에서 추출
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(orderService.getMyOrder(userId, orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-User-Id") Long userId, // TODO: 인증 적용 후 SecurityContext에서 추출
            @PathVariable Long orderId
    ) {
        orderService.cancel(userId, orderId);

        return ResponseEntity.noContent().build();
    }
}
