package com.dropit.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateRequest(
        @NotNull(message = "주문 항목은 필수입니다.")
        @Size(min = 1, max = 1, message = "현재 주문 항목은 1개만 입력할 수 있습니다.")
        List<@Valid OrderItemCreateRequest> items
) {
}
